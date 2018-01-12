/*
 *    Copyright 2017 OICR
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package io.dockstore.client

import java.io.{File => JFile}
import java.util

import spray.json._
import wdl4s._
import wdl4s.types.{WdlArrayType, WdlFileType}
import wdl4s.values.WdlValue

import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

/**
  * This exposes the Cromwell methods in an easier way to access from Java
  * until wdltool is released to artifactory.
  */
class Bridge {
  var secondaryWdlFiles = new util.HashMap[String, String]()
  val bridgeHelper = new BridgeHelper()

  def setSecondaryFiles(secondaryFiles: util.HashMap[String, String]) = {
    secondaryWdlFiles = secondaryFiles
  }

  def main(args: Array[String]): Unit = {
    println("Hello, world!")
  }

  def inputs(args: Seq[String]): String = {
    loadWdl(args.head) { namespace =>
      import wdl4s.types.WdlTypeJsonFormatter._
      namespace match {
        case x: NamespaceWithWorkflow => x.workflow.inputs.toJson.prettyPrint
      }
    }
  }

  private[this] def loadWdl(path: String)(f: WdlNamespace => String): String = {
    val lines = scala.io.Source.fromFile(new JFile(path)).mkString
    Try(NamespaceWithWorkflow.load(lines, launchResolver)) match {
      case Success(namespace) => f(namespace)
      case Failure(t) =>
        println(t.getMessage)
        null
    }
  }

  def dagResolver(importString: String): WdlSource = {
    importString match {
      case s if (s.startsWith("http://") || s.startsWith("https://")) =>
        bridgeHelper.resolveUrl(s)
      case s =>
        bridgeHelper.resolveSecondaryPath(s, secondaryWdlFiles)
    }
  }

  def launchResolver(importString: String): WdlSource = {
    importString match {
      case s if (s.startsWith("http://") || s.startsWith("https://")) =>
        bridgeHelper.resolveUrl(s)
      case s =>
        bridgeHelper.resolveLocalPath(s)
    }
  }

  def getInputFiles(file: JFile): util.Map[String, String] = {
    val lines = scala.io.Source.fromFile(file).mkString
    val ns = NamespaceWithWorkflow.load(lines, launchResolver)

    val inputList = new util.HashMap[String, String]()

    ns.workflow.inputs foreach { case (key, value) =>
      if (value.wdlType == WdlFileType || value.wdlType == WdlArrayType(WdlFileType)) {
        inputList.put(value.fqn, value.wdlType.toWdlString)
      }
    }
    inputList
  }

  def getImportFiles(file: JFile): util.ArrayList[String] = {
    val lines = scala.io.Source.fromFile(file).mkString
    val importList = new util.ArrayList[String]()

    val ns = NamespaceWithWorkflow.load(lines, dagResolver)

    ns.imports foreach { imported =>
      println(imported.uri)
      importList.add(imported.uri)
    }

    importList
  }

  def getImportMap(file: JFile): util.LinkedHashMap[String, String] = {
    val lines = scala.io.Source.fromFile(file).mkString
    val importMap = new util.LinkedHashMap[String, String]()

    val ns = NamespaceWithWorkflow.load(lines, dagResolver)

    ns.imports foreach { imported =>
      val importNamespace = imported.namespace.get
      if (!importNamespace.isEmpty) {
        importMap.put(importNamespace, imported.uri)
      }
    }

    importMap
  }


  def getOutputFiles(file: JFile): util.List[String] = {
    val lines = scala.io.Source.fromFile(file).mkString
    val ns = NamespaceWithWorkflow.load(lines, launchResolver)

    val outputList = new util.ArrayList[String]()

    ns.workflow.outputs.seq foreach { value =>
      if (value.wdlType == WdlFileType || value.wdlType == WdlArrayType(WdlFileType)) {
        outputList.add(value.fullyQualifiedName)
      }
    }
    outputList
  }

  val passthrough = new PartialFunction[WdlValue, WdlValue] {
    def apply(x: WdlValue) = x

    def isDefinedAt(x: WdlValue) = true
  }

  def getCallsAndDocker(file: JFile): util.LinkedHashMap[String, Seq[String]] = {
    val lines = scala.io.Source.fromFile(file).mkString
    val ns = NamespaceWithWorkflow.load(lines, dagResolver)
    val tasks = new util.LinkedHashMap[String, Seq[String]]()

    // For each call
    //ns.workflow.collectAllScatters foreach { scatter => print(scatter.collectAllCalls foreach(call => println(call.task.name)))}
    ns.workflow.collectAllCalls foreach { call =>
      // Find associated task (Should only be one)
      ns.findTask(call.unqualifiedName) foreach { task =>
        try {
          // Get the list of docker images
          val dockerAttributes = task.runtimeAttributes.attrs.get("docker")
          val attributes = if (dockerAttributes.isDefined) dockerAttributes.get.collectAsSeq(passthrough).map(x => x.toWdlString.replaceAll("\"", "")) else null
          var name = call.alias.toString
          tasks.put(task.name, attributes)
        } catch {
          // Throws error if task has no runtime section or a runtime section but no docker (we stop error from being thrown)
          case e: NoSuchElementException =>
        }
      }
    }
    tasks
  }

  def getCallsToDockerMap(file: JFile): util.LinkedHashMap[String, String] = {
    val lines = scala.io.Source.fromFile(file).mkString
    val ns = NamespaceWithWorkflow.load(lines, dagResolver)
    val tasks = new util.LinkedHashMap[String, String]()


    ns.workflow.calls foreach { call =>
      val task = call.task
      val dockerAttributes = task.runtimeAttributes.attrs.get("docker")
      tasks.put("dockstore_" + call.unqualifiedName, if (dockerAttributes.isDefined) dockerAttributes.get.collectAsSeq(passthrough).map(x => x.toWdlString.replaceAll("\"", "")).mkString("") else null)
    }
    tasks
  }

  def getCallsToDependencies(file: JFile): util.LinkedHashMap[String, util.List[String]] = {
    val lines = scala.io.Source.fromFile(file).mkString
    val ns = NamespaceWithWorkflow.load(lines, dagResolver)
    val dependencyMap = new util.LinkedHashMap[String, util.List[String]]()
    ns.workflow.calls foreach { call =>
      val dependencies = new util.ArrayList[String]()
      call.inputMappings foreach { case (key, value) =>
        value.prerequisiteCallNames foreach { inputDependency =>
          dependencies.add("dockstore_" + inputDependency)
        }
      }
      dependencyMap.put("dockstore_" + call.unqualifiedName, dependencies)
    }
    dependencyMap
  }


}