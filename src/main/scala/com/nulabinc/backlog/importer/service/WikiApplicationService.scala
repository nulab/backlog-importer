package com.nulabinc.backlog.importer.service

import javax.inject.Inject

import com.nulabinc.backlog.migration.conf.{BacklogConstantValue, BacklogPaths}
import com.nulabinc.backlog.migration.converter.BacklogUnmarshaller
import com.nulabinc.backlog.migration.domain.{BacklogAttachment, BacklogWiki}
import com.nulabinc.backlog.migration.service._
import com.nulabinc.backlog.migration.utils.{ConsoleOut, IOUtil, Logging, ProgressBar}
import com.osinka.i18n.Messages

import scalax.file.Path

/**
  * @author uchida
  */
class WikiApplicationService @Inject()(backlogPaths: BacklogPaths, wikiService: WikiService, sharedFileService: SharedFileService) extends Logging {

  def execute(projectId: Long, propertyResolver: PropertyResolver) = {
    val paths    = IOUtil.directoryPaths(backlogPaths.wikiDirectoryPath)
    val allWikis = wikiService.allWikis()

    def exists(wikiName: String): Boolean = {
      allWikis.exists(wiki => wiki.name == wikiName)
    }

    def condition(path: Path): Boolean = {
      unmarshal(path) match {
        case Some(wiki) =>
          if (wiki.name == BacklogConstantValue.WIKI_HOME_NAME) false else exists(wiki.name)
        case _ => false
      }
    }

    val console  = (ProgressBar.progress _)(Messages("common.wikis"), Messages("message.importing"), Messages("message.imported"))
    val wikiDirs = paths.filterNot(condition)
    wikiDirs.zipWithIndex.foreach {
      case (wikiDir, index) =>
        for {
          wiki    <- unmarshal(wikiDir)
          created <- create(projectId, propertyResolver, wiki)
        } yield postCreate(created.id, wikiDir, wiki)
        console(index + 1, wikiDirs.size)
    }
  }

  private[this] def create(projectId: Long, propertyResolver: PropertyResolver, wiki: BacklogWiki): Option[BacklogWiki] = {
    if (wiki.name == BacklogConstantValue.WIKI_HOME_NAME)
      wikiService.update(wiki)
    else
      Some(wikiService.create(projectId, wiki, propertyResolver))
  }

  private[this] def postCreate(createdId: Long, wikiDir: Path, wiki: BacklogWiki) = {
    wikiService.addAttachment(createdId, postAttachments(wikiDir, wiki)) match {
      case Right(_) =>
      case Left(e) =>
        ConsoleOut.error(Messages("import.error.wiki.attachment", wiki.name, e.getMessage))
    }
    sharedFileService.linkWikiSharedFile(createdId, wiki)
  }

  private[this] def postAttachments(wikiDir: Path, wiki: BacklogWiki): Seq[BacklogAttachment] = {
    val paths = wiki.attachments.flatMap(attachment => toPath(attachment, wikiDir))
    paths.flatMap { path =>
      wikiService.postAttachment(path.path) match {
        case Right(attachment) => Some(attachment)
        case Left(e) =>
          if (e.getMessage.contains("The size of attached file is too large."))
            ConsoleOut.error(Messages("import.error.attachment.too_large", path.name))
          else
            ConsoleOut.error(Messages("import.error.issue.attachment", path.name, e.getMessage))
          None
      }
    }
  }

  private[this] def toPath(attachment: BacklogAttachment, wikiDir: Path): Option[Path] = {
    val files = backlogPaths.wikiAttachmentPath(wikiDir).toAbsolute.children()
    files.find(file => file.name == attachment.fileName) match {
      case Some(file) => Some(file)
      case _          => None
    }
  }

  private[this] def unmarshal(path: Path): Option[BacklogWiki] =
    BacklogUnmarshaller.wiki(backlogPaths.wikiJson(path))

}
