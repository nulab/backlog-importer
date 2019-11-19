package com.nulabinc.backlog.migration.importer.core

import com.nulabinc.backlog.migration.common.conf.ExcludeOption

case class ImportConfig(fitIssueKey: Boolean, retryCount: Int, excludeOption: ExcludeOption)
