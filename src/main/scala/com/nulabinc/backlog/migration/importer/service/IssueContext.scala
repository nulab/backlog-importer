package com.nulabinc.backlog.migration.importer.service

import com.nulabinc.backlog.migration.common.domain.{BacklogIssue, BacklogProject}
import com.nulabinc.backlog.migration.common.service.PropertyResolver
import com.nulabinc.backlog.migration.common.utils.Logging

import scala.collection.mutable

/**
  * @author uchida
  */
case class IssueContext(project: BacklogProject, propertyResolver: PropertyResolver) extends Logging {

  var optPrevIssueIndex: Option[Int]             = None
  val toRemoteIssueId                            = (localIssueId: Long) => issueIdMap.get(localIssueId): Option[Long]
  val excludeIssueIds: mutable.ArrayBuffer[Long] = mutable.ArrayBuffer()

  private[this] val issueIdMap: mutable.Map[Long, Long] = mutable.Map()

  def addIssueId(backlogIssue: BacklogIssue, remoteIssue: BacklogIssue) = {
    issueIdMap += backlogIssue.id -> remoteIssue.id
  }

}
