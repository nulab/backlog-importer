package com.nulabinc.backlog.migration.importer.controllers

import com.google.inject.Guice
import com.nulabinc.backlog.migration.common.conf.BacklogApiConfiguration
import com.nulabinc.backlog.migration.common.utils.{ConsoleOut, Logging}
import com.nulabinc.backlog.migration.importer.modules.BacklogModule
import com.nulabinc.backlog.migration.importer.service.ProjectApplicationService
import com.osinka.i18n.Messages

/**
  * @author uchida
  */
object ImportController extends Logging {

  def execute(apiConfig: BacklogApiConfiguration, fitIssueKey: Boolean) = {

    val injector = Guice.createInjector(new BacklogModule(apiConfig, fitIssueKey))

    ConsoleOut.println(s"""
         |${Messages("import.start")}
         |--------------------------------------------------""".stripMargin)

    val service = injector.getInstance(classOf[ProjectApplicationService])
    service.execute()

  }

}
