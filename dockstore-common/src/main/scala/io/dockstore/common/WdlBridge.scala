package io.dockstore.common


import java.nio.file.{Files, Paths}
import java.util

import com.typesafe.config.ConfigFactory
import common.validation.ErrorOr.ErrorOr
import cromwell.core.path.DefaultPathBuilder
import cromwell.languages.util.ImportResolver.{DirectoryResolver, HttpResolver, ImportResolver, ResolvedImportBundle}
import languages.wdl.draft3.WdlDraft3LanguageFactory
import wdl.draft3.parser.WdlParser
import wom.executable.WomBundle
import cats.syntax.validated._
import common.Checked
import common.validation.Checked._
import cromwell.languages.util.ImportResolver

import scala.collection.JavaConverters._
import scala.util.Try

/**
  * A bridge class for interacting with the WDL draft-3/1.0
  */
class WdlBridge {
  var secondaryWdlFiles = new util.HashMap[String, String]()

  def main(args: Array[String]): Unit = {
    println("WdlBridge")
  }

  /**
    * Set the secondary files (imports)
    * @param secondaryFiles
    */
  def setSecondaryFiles(secondaryFiles: util.HashMap[String, String]): Unit = {
    secondaryWdlFiles = secondaryFiles
  }

  /**
    * Validates the workflow given by filePath
    * @param filePath absolute path to file
    */
  @throws(classOf[WdlParser.SyntaxError])
  def validateWorkflow(filePath: String) = {
    val bundle = getBundle(filePath)
    // isLeft == true => invalid
    if (bundle.isLeft) {
      throw new WdlParser.SyntaxError(bundle.left.get.head)
    }
  }

  /**
    * Validates the tool given by filePath
    * @param filePath absolute path to file
    */
  @throws(classOf[WdlParser.SyntaxError])
  def validateTool(filePath: String) = {
    validateWorkflow(filePath)
  }

  /**
    * Returns true if the file is likely v1.0, false otherwise.
    * Will only look at the primary descriptor.
    * @param filePath absolute path to file
    * @return whether file looks parsable or not
    */
  def isDraft3(filePath: String) : Boolean = {
    val draft3Factory = new WdlDraft3LanguageFactory(ConfigFactory.empty())
    val fileContent = readFile(filePath)
    draft3Factory.looksParsable(fileContent)
  }

  /**
    * Get the WomBundle for a workflow
    * @param filePath absolute path to file
    * @return WomBundle
    */
  def getBundle(filePath: String): common.Checked[WomBundle] = {
    val fileContent = readFile(filePath)
    val draft3Factory = new WdlDraft3LanguageFactory(ConfigFactory.empty())
    val filePathObj = DefaultPathBuilder.build(filePath).get

    // Resolve from mapping, local filesystem, or http import
    val mapResolver = MapResolver()
    mapResolver.setSecondaryFiles(secondaryWdlFiles)

    lazy val importResolvers: List[ImportResolver] =
      DirectoryResolver.localFilesystemResolvers(Some(filePathObj)) :+ HttpResolver(relativeTo = None) :+ mapResolver

    draft3Factory.getWomBundle(fileContent, "{}", importResolvers, List(draft3Factory))
  }

  /**
    * Read the given file into a string
    * @param filePath absolute path to file
    * @return Content of file as a string
    */
  def readFile(filePath: String): String = Try(Files.readAllLines(Paths.get(filePath)).asScala.mkString(System.lineSeparator())).get

}

/**
  * Class for resolving imports defined in memory
  */
case class MapResolver() extends ImportResolver {
  var secondaryWdlFiles = new util.HashMap[String, String]()

  def setSecondaryFiles(secondaryFiles: util.HashMap[String, String]): Unit = {
    secondaryWdlFiles = secondaryFiles
  }

  override def name: String = "Map importer"

  override protected def innerResolver(path: String, currentResolvers: List[ImportResolver]): Checked[ImportResolver.ResolvedImportBundle] = {
    val importPath = path.replaceFirst("file://", "")
    val content = secondaryWdlFiles.get(importPath)
    val updatedResolvers = currentResolvers map {
      case d if d == this => MapResolver()
      case other => other
    }
    ResolvedImportBundle(content, updatedResolvers).validNelCheck
  }

  override def cleanupIfNecessary(): ErrorOr[Unit] = ().validNel
}
