package com.nulabinc.backlog.importer.controllers

import com.google.inject.Guice
import com.nulabinc.backlog.importer.modules.BacklogModule
import com.nulabinc.backlog.importer.service.ProjectApplicationService
import com.nulabinc.backlog.migration.conf.BacklogApiConfiguration
import com.nulabinc.backlog.migration.utils.{ConsoleOut, Logging}
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
