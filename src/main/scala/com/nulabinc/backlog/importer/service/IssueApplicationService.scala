package com.nulabinc.backlog.importer.service

import javax.inject.{Inject, Named}

import com.nulabinc.backlog.migration.conf.BacklogPaths
import com.nulabinc.backlog.migration.converter.BacklogUnmarshaller
import com.nulabinc.backlog.migration.domain.{BacklogComment, BacklogIssue, BacklogProject}
import com.nulabinc.backlog.migration.service._
import com.nulabinc.backlog.migration.utils._
import com.osinka.i18n.Messages

import scalax.file.Path

/**
  * @author uchida
  */
class IssueApplicationService @Inject()(@Named("fitIssueKey") fitIssueKey: Boolean,
                                        backlogPaths: BacklogPaths,
                                        sharedFileService: SharedFileService,
                                        issueService: IssueService,
                                        commentService: CommentService)
    extends Logging {

  def execute(project: BacklogProject, propertyResolver: PropertyResolver) = {

    ConsoleOut.println("""
      """.stripMargin)

    val console          = new IssueProgressBar(totalSize())
    implicit val context = IssueContext(project, propertyResolver, console)
    val paths            = IOUtil.directoryPaths(backlogPaths.issueDirectoryPath).sortWith(_.name < _.name)
    paths.zipWithIndex.foreach {
      case (path, index) =>
        loadDateDirectory(path, index)
    }
  }

  private[this] def loadDateDirectory(path: Path, index: Int)(implicit ctx: IssueContext) = {
    val jsonDirs = path.toAbsolute.children().filter(_.isDirectory).toSeq.sortWith(compareIssueJsons)
    ctx.console.date = DateUtil.yyyymmddToSlashFormat(path.name)
    ctx.console.failed = 0

    jsonDirs.zipWithIndex.foreach {
      case (jsonDir, index) =>
        loadJson(jsonDir, index, jsonDirs.size)
    }
  }

  private[this] def loadJson(path: Path, index: Int, size: Int)(implicit ctx: IssueContext) = {
    BacklogUnmarshaller.issue(backlogPaths.issueJson(path)) match {
      case Some(issue: BacklogIssue)     => createIssue(issue, index, size)
      case Some(comment: BacklogComment) => createComment(comment, path, index, size)
      case _                             => None
    }
    ctx.console.count = ctx.console.count + 1
  }

  private[this] def createIssue(issue: BacklogIssue, index: Int, size: Int)(implicit ctx: IssueContext) = {
    createDummyIssues(issue)

    if (issueService.exists(ctx.project.id, issue)) {
      ctx.excludeIssueIds += issue.id
      for { remoteIssue <- issueService.optIssueOfParams(ctx.project.id, issue) } yield {
        ctx.addIssueId(issue, remoteIssue)
      }
      ctx.console.warning(Messages("import.issue.already_exists", issue.optIssueKey.getOrElse(issue.id.toString)))
    } else {
      issueService.create(issueService.setCreateParam(ctx.project.id, ctx.propertyResolver, ctx.toRemoteIssueId, issueService.issueOfId))(issue) match {
        case Right(remoteIssue) =>
          sharedFileService.linkIssueSharedFile(remoteIssue.id, issue)
          ctx.addIssueId(issue, remoteIssue)
        case _ =>
          ctx.console.failed += 1
      }
      ctx.console.progress(index + 1, size)
    }
  }

  private[this] def createDummyIssues(issue: BacklogIssue)(implicit ctx: IssueContext) = {
    val optIssueIndex = issue.optIssueKey.map(IssueKeyUtil.findIssueIndex)
    for {
      prevIssueIndex <- ctx.optPrevIssueIndex
      issueIndex     <- optIssueIndex
      if ((prevIssueIndex + 1) != issueIndex)
      if (fitIssueKey)
    } yield ((prevIssueIndex + 1) until issueIndex).foreach(createDummyIssue)
    ctx.optPrevIssueIndex = optIssueIndex
  }

  private[this] def createDummyIssue(index: Int)(implicit ctx: IssueContext) = {
    val dummyIssue = issueService.createDummy(ctx.project.id, ctx.propertyResolver)
    issueService.delete(dummyIssue.getId)
    ctx.console.warning(s"${Messages("import.issue.create_dummy", s"${ctx.project.key}-${index}")}")
  }

  private[this] def createComment(comment: BacklogComment, path: Path, index: Int, size: Int)(implicit ctx: IssueContext) = {
    for {
      issueId       <- comment.optIssueId
      remoteIssueId <- ctx.toRemoteIssueId(issueId)
    } yield {
      if (!ctx.excludeIssueIds.contains(issueId)) {
        commentService.update(commentService.setUpdateParam(remoteIssueId, path, ctx.propertyResolver, ctx.toRemoteIssueId))(comment) match {
          case Left(e) if (Option(e.getMessage).getOrElse("").contains("Please change the status or post a comment.")) =>
            logger.warn(e.getMessage, e)
          case Left(e) =>
            logger.error(e.getMessage, e)
            ctx.console.failed += 1
          case _ =>
        }
        ctx.console.progress(index + 1, size)
      }
    }
  }

  private[this] def compareIssueJsons(path1: Path, path2: Path): Boolean = {
    def getTimestamp(value: String): Long = value.split("-")(0).toLong

    def getIssueId(value: String): Long = value.split("-")(1).toLong

    def getType(value: String) = value.split("-")(2)

    def getIndex(value: String) = value.split("-")(3).toInt

    if (getTimestamp(path1.name) == getTimestamp(path2.name)) {
      if (getType(path1.name) == getType(path2.name))
        if (getIssueId(path1.name) == getIssueId(path2.name))
          getIndex(path1.name) < getIndex(path2.name)
        else getIssueId(path1.name) < getIssueId(path2.name)
      else getType(path1.name) > getType(path2.name)
    } else getTimestamp(path1.name) < getTimestamp(path2.name)
  }

  private[this] def totalSize(): Int = {
    val paths = IOUtil.directoryPaths(backlogPaths.issueDirectoryPath)
    paths.foldLeft(0) { (count, path) =>
      count + path.toAbsolute.children().size
    }
  }

}
