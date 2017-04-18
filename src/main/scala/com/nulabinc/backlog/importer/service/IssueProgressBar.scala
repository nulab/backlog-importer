package com.nulabinc.backlog.importer.service

import java.util.Date

import com.nulabinc.backlog.migration.utils.{ConsoleOut, DateUtil, Logging, ProgressBar}
import com.osinka.i18n.Messages
import org.fusesource.jansi.Ansi
import org.fusesource.jansi.Ansi.ansi
import org.fusesource.jansi.Ansi.Color._

/**
  * @author uchida
  */
class IssueProgressBar(totalSize: Int) extends Logging {

  var count   = 0
  var failed  = 0
  var date    = ""
  var newLine = false

  val timer = (timerFunc _)()

  private[this] def timerFunc() = {
    var tempTime: Long         = System.currentTimeMillis()
    var totalElapsedTime: Long = 0
    () =>
      {
        val elapsedTime: Long = System.currentTimeMillis() - tempTime
        totalElapsedTime = totalElapsedTime + elapsedTime
        val average: Float = totalElapsedTime.toFloat / count.toFloat
        tempTime = System.currentTimeMillis()
        val remaining           = totalSize - count
        val remainingTime: Long = (remaining * average).toLong

        DateUtil.timeFormat(new Date(remainingTime))
      }: String
  }

  def warning(value: String) = {
    newLine = true
    clear()
    val message =
      s"""${(" " * 11) + ansi().fg(YELLOW).a(value).reset().toString}
        |--------------------------------------------------
        |${remaining()}""".stripMargin
    ConsoleOut.outStream.println(message)
  }

  def error(value: String) = {
    newLine = true
    clear()
    val message =
      s"""${(" " * 11) + ansi().fg(RED).a(value).reset().toString}
         |--------------------------------------------------
         |${remaining()}""".stripMargin
    ConsoleOut.outStream.println(message)
  }

  def progress(indexOfDate: Int, totalOfDate: Int) = {
    newLine = (indexOfDate == 1)
    clear()
    val message =
      s"""${current(indexOfDate, totalOfDate)}
         |--------------------------------------------------
         |${remaining()}""".stripMargin
    ConsoleOut.outStream.println(message)
  }

  private[this] def clear() = {
    if (newLine) {
      ConsoleOut.outStream.println()
    }
    (0 until 3).foreach { _ =>
      ConsoleOut.outStream.print(ansi.cursorLeft(999).cursorUp(1).eraseLine(Ansi.Erase.ALL))
    }
    ConsoleOut.outStream.flush()
  }

  private[this] def current(indexOfDate: Int, totalOfDate: Int): String = {
    val progressBar  = ProgressBar.progressBar(indexOfDate, totalOfDate)
    val resultString = if (failed == 0) Messages("common.result_success") else Messages("common.result_failed", failed)
    val result = if (resultString.nonEmpty) {
      if (resultString == Messages("common.result_success"))
        s"[${ansi().fg(GREEN).a(resultString).reset()}]"
      else s"[${ansi().fg(RED).a(resultString).reset()}]"
    } else resultString

    val message =
      Messages("import.date.execute",
               date,
               Messages("common.issues"),
               if (indexOfDate == totalOfDate) Messages("message.imported") else Messages("message.importing"))

    s"${progressBar}${result} ${message}"
  }

  private[this] def remaining(): String = {
    val progressBar = ProgressBar.progressBar(count + 1, totalSize)
    val message     = Messages("import.progress", count + 1, totalSize)
    val time        = Messages("import.remaining_time", timer())
    s"${progressBar} ${message}${time}"
  }

}
