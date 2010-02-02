/*
 * Copyright (c) 2009 Matthew Hildebrand <matt.hildebrand@gmail.com>
 *
 * Permission to use, copy, modify, and/or distribute this software for any
 * purpose with or without fee is hereby granted, provided that the above
 * copyright notice and this permission notice appear in all copies.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
 * ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 * ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
 * OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 */


package org.fusesource.scalate.ssp

import org.fusesource.scalate._
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.net.URLClassLoader
import scala.tools.nsc.Global
import scala.tools.nsc.Settings
import scala.tools.nsc.reporters.ConsoleReporter
import org.fusesource.scalate.util.ClassLoaders._
import org.fusesource.scalate.util.Logging


class ScalaCompiler() extends Compiler with Logging {

  override def compile(code: String, sourceDirectory: File, bytecodeDirectory: File, classpath: String): Unit = {
    // Prepare an object for collecting error messages from the compiler
    val messageCollector = new StringWriter
    val messageCollectorWrapper = new PrintWriter(messageCollector)

    // Initialize the compiler
    val settings = generateSettings(bytecodeDirectory, classpath)
    val reporter = new ConsoleReporter(settings, Console.in, messageCollectorWrapper)
    val compiler = new Global(settings, reporter)

    // Attempt compilation
    (new compiler.Run).compile(findFiles(sourceDirectory).map(_.toString))

    // Bail out if compilation failed
    if (reporter.hasErrors) {
      reporter.printSummary
      messageCollectorWrapper.close
      throw new ServerPageException("Compilation failed:\n" + messageCollector.toString)
    }
  }


  private def error(message: String): Unit = throw new ServerPageException("Compilation failed:\n" + message)

  private def generateSettings(bytecodeDirectory: File, classpath: String): Settings = {

    def useCP = if (classpath != null) {
      classpath
    } else {
      (classLoaderList(Thread.currentThread.getContextClassLoader) ::: classLoaderList(getClass) ::: classLoaderList(ClassLoader.getSystemClassLoader) ).mkString(":")
    }

    fine("using classpath: " + useCP)

    val settings = new Settings(error)
    settings.classpath.value = useCP
    settings.outdir.value = bytecodeDirectory.toString
    settings.deprecation.value = true
    settings.unchecked.value = true
    settings
  }


  private def findFiles(root: File): List[File] = {
    if (root.isFile)
      List(root)
    else
      makeList(root.listFiles).flatMap {f => findFiles(f)}
  }


  private def makeList(a: Array[File]): List[File] = {
    if (a == null)
      Nil
    else
      a.toList
  }

}