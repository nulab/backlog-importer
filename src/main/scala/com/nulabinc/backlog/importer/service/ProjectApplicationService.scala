package com.nulabinc.backlog.importer.service

import javax.inject.Inject

import com.nulabinc.backlog.migration.conf.BacklogPaths
import com.nulabinc.backlog.migration.converter.BacklogUnmarshaller
import com.nulabinc.backlog.migration.domain._
import com.nulabinc.backlog.migration.service.{PropertyResolver, _}
import com.nulabinc.backlog.migration.utils.{ConsoleOut, Logging, ProgressBar}
import com.osinka.i18n.Messages
import org.fusesource.jansi.Ansi
import org.fusesource.jansi.Ansi.ansi

/**
  * @author uchida
  */
class ProjectApplicationService @Inject()(backlogPaths: BacklogPaths,
                                          groupService: GroupService,
                                          projectService: ProjectService,
                                          versionService: VersionService,
                                          projectUserService: ProjectUserService,
                                          issueTypeService: IssueTypeService,
                                          issueCategoryService: IssueCategoryService,
                                          customFieldSettingService: CustomFieldSettingService,
                                          wikiApplicationService: WikiApplicationService,
                                          issueApplicationService: IssueApplicationService,
                                          resolutionService: ResolutionService,
                                          userService: UserService,
                                          statusService: StatusService,
                                          priorityService: PriorityService)
    extends Logging {

  def execute() = {
    for {
      project <- BacklogUnmarshaller.project(backlogPaths)
    } yield {
      projectService.create(project) match {
        case Right(project) =>
          preExecute()
          contents(project)
          postExecute()

          ConsoleOut.outStream.print(ansi.cursorLeft(999).cursorUp(1).eraseLine(Ansi.Erase.ALL))
          ConsoleOut.outStream.print(ansi.cursorLeft(999).cursorUp(1).eraseLine(Ansi.Erase.ALL))
          ConsoleOut.outStream.flush()

          ConsoleOut.println(s"""|--------------------------------------------------
                                 |${Messages("import.finish")}""".stripMargin)
        case Left(e) =>
          if (e.getMessage.contains("Project limit."))
            ConsoleOut.error(Messages("import.error.limit.project", project.key))
          else if (e.getMessage.contains("Duplicate entry"))
            ConsoleOut.error(Messages("import.error.project.not.join", project.key))
          else {
            logger.error(e.getMessage, e)
            ConsoleOut.error(Messages("import.error.failed.import", project.key, e.getMessage))
          }
          ConsoleOut.println(s"""|--------------------------------------------------
                                 |${Messages("import.suspend")}""".stripMargin)
      }
    }
  }

  private[this] def contents(project: BacklogProject) = {
    val propertyResolver = buildPropertyResolver()

    //Wiki
    wikiApplicationService.execute(project.id, propertyResolver)

    //Issue
    issueApplicationService.execute(project, propertyResolver)
  }

  private[this] def preExecute() = {
    val propertyResolver = buildPropertyResolver()
    importGroup(propertyResolver)
    importProjectUser(propertyResolver)
    importVersion()
    importCategory()
    importIssueType()
    importCustomField()
  }

  private[this] def postExecute() = {
    val propertyResolver = buildPropertyResolver()

    removeVersion(propertyResolver)
    removeCategory(propertyResolver)
    removeIssueType(propertyResolver)
    removeCustomField(propertyResolver)

    BacklogUnmarshaller.backlogCustomFieldSettings(backlogPaths).filter(!_.delete).foreach { customFieldSetting =>
      customFieldSettingService.update(customFieldSettingService.setUpdateParams(propertyResolver))(customFieldSetting)
    }
  }

  private[this] def importGroup(propertyResolver: PropertyResolver) = {
    val groups = groupService.allGroups()
    def exists(group: BacklogGroup): Boolean = {
      groups.exists(_.name == group.name)
    }
    val backlogGroups = BacklogUnmarshaller.groups(backlogPaths).filterNot(exists)
    val console       = (ProgressBar.progress _)(Messages("common.groups"), Messages("message.importing"), Messages("message.imported"))
    backlogGroups.zipWithIndex.foreach {
      case (backlogGroup, index) =>
        groupService.create(backlogGroup, propertyResolver)
        console(index + 1, backlogGroups.size)
    }
  }

  private[this] def importVersion() = {
    val versions = versionService.allVersions()
    def exists(version: BacklogVersion): Boolean = {
      versions.exists(_.name == version.name)
    }
    val backlogVersions = BacklogUnmarshaller.versions(backlogPaths).filterNot(exists)
    val console         = (ProgressBar.progress _)(Messages("common.version"), Messages("message.importing"), Messages("message.imported"))
    backlogVersions.zipWithIndex.foreach {
      case (backlogVersion, index) =>
        versionService.add(backlogVersion)
        console(index + 1, backlogVersions.size)
    }
  }

  private[this] def importCategory() = {
    val categories = issueCategoryService.allIssueCategories()
    def exists(issueCategory: BacklogIssueCategory): Boolean = {
      categories.exists(_.name == issueCategory.name)
    }
    val issueCategories = BacklogUnmarshaller.issueCategories(backlogPaths).filterNot(exists)
    val console         = (ProgressBar.progress _)(Messages("common.category"), Messages("message.importing"), Messages("message.imported"))
    issueCategories.zipWithIndex.foreach {
      case (issueCategory, index) =>
        issueCategoryService.add(issueCategory)
        console(index + 1, issueCategories.size)
    }
  }

  private[this] def importIssueType() = {
    val issueTypes = issueTypeService.allIssueTypes()
    def exists(issueType: BacklogIssueType): Boolean = {
      issueTypes.exists(_.name == issueType.name)
    }
    val backlogIssueTypes = BacklogUnmarshaller.issueTypes(backlogPaths).filterNot(exists)
    val console           = (ProgressBar.progress _)(Messages("common.issue_type"), Messages("message.importing"), Messages("message.imported"))
    backlogIssueTypes.zipWithIndex.foreach {
      case (backlogIssueType, index) =>
        issueTypeService.add(backlogIssueType)
        console(index + 1, backlogIssueTypes.size)
    }
  }

  private[this] def importProjectUser(propertyResolver: PropertyResolver) = {
    val projectUsers = BacklogUnmarshaller.projectUsers(backlogPaths)
    val console      = (ProgressBar.progress _)(Messages("common.project_user"), Messages("message.importing"), Messages("message.imported"))
    projectUsers.zipWithIndex.foreach {
      case (projectUser, index) =>
        for {
          userId <- projectUser.optUserId
          id     <- propertyResolver.optResolvedUserId(userId)
        } yield projectUserService.add(id)
        console(index + 1, projectUsers.size)
    }
  }

  private[this] def importCustomField() = {
    val customFieldSettings = customFieldSettingService.allCustomFieldSettings()
    def exists(setting: BacklogCustomFieldSetting): Boolean = {
      customFieldSettings.exists(_.name == setting.name)
    }
    val backlogCustomFields = BacklogUnmarshaller.backlogCustomFieldSettings(backlogPaths).filterNot(exists)
    val console             = (ProgressBar.progress _)(Messages("common.custom_field"), Messages("message.importing"), Messages("message.imported"))
    backlogCustomFields.zipWithIndex.foreach {
      case (backlogCustomField, index) =>
        customFieldSettingService.add(customFieldSettingService.setAddParams)(backlogCustomField)
        console(index + 1, backlogCustomFields.size)
    }
  }

  private[this] def removeVersion(propertyResolver: PropertyResolver) = {
    BacklogUnmarshaller.versions(backlogPaths).filter(_.delete).foreach { version =>
      for {
        versionId <- propertyResolver.optResolvedVersionId(version.name)
      } yield versionService.remove(versionId)
    }
  }

  private[this] def removeCategory(propertyResolver: PropertyResolver) = {
    BacklogUnmarshaller.issueCategories(backlogPaths).filter(_.delete).foreach { category =>
      for {
        issueCategoryId <- propertyResolver.optResolvedCategoryId(category.name)
      } yield issueCategoryService.remove(issueCategoryId)
    }
  }

  private[this] def removeIssueType(propertyResolver: PropertyResolver) = {
    BacklogUnmarshaller.issueTypes(backlogPaths).filter(_.delete).foreach { issueType =>
      for {
        issueTypeId <- propertyResolver.optResolvedIssueTypeId(issueType.name)
      } yield {
        issueTypeService.remove(issueTypeId, propertyResolver.tryDefaultIssueTypeId())
      }
    }
  }

  private[this] def removeCustomField(propertyResolver: PropertyResolver) =
    BacklogUnmarshaller.backlogCustomFieldSettings(backlogPaths).filter(_.delete).foreach { backlogCustomFieldSetting =>
      for {
        targetCustomFieldSetting <- propertyResolver.optResolvedCustomFieldSetting(backlogCustomFieldSetting.name)
        customFieldSettingId     <- targetCustomFieldSetting.optId
      } yield customFieldSettingService.remove(customFieldSettingId)
    }

  private[this] def buildPropertyResolver(): PropertyResolver = {
    new PropertyResolverImpl(customFieldSettingService,
                             issueTypeService,
                             issueCategoryService,
                             versionService,
                             resolutionService,
                             userService,
                             statusService,
                             priorityService)
  }

}
