package com.nulabinc.backlog.migration.importer.modules

import com.google.inject.name.Names
import com.nulabinc.backlog.migration.common.conf.BacklogApiConfiguration
import com.nulabinc.backlog.migration.common.modules.DefaultModule

/**
  * @author uchida
  */
private[importer] class BacklogModule(apiConfig: BacklogApiConfiguration, fitIssueKey: Boolean) extends DefaultModule(apiConfig) {

  override def configure() = {
    super.configure()
    bind(classOf[BacklogApiConfiguration]).toInstance(apiConfig)
    bind(classOf[Boolean]).annotatedWith(Names.named("fitIssueKey")).toInstance(fitIssueKey)
  }

}
