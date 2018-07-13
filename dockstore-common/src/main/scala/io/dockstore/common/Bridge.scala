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

package io.dockstore.common

import java.io.{File => JFile}
import java.util

import io.github.collaboratory.wdl.BridgeHelper
import spray.json._
import wdl4s.parser.WdlParser
import wdl4s.wdl.WdlNamespace
import wdl4s.wdl.types.{WdlArrayType, WdlFileType}
import wdl4s.wdl.values.WdlValue
import wdl4s.wdl.{WdlNamespaceWithWorkflow, WorkflowSource}

import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

/**
  * This exposes the Cromwell methods in an easier way to access from Java
  * until wdltool is released to artifactory.
  */
class Bridge(basePath : String) {
  var secondaryWdlFiles = new util.HashMap[String, String]()
  val bridgeHelper = new BridgeHelper()

  def setSecondaryFiles(secondaryFiles: util.HashMap[String, String]): Unit = {
    secondaryWdlFiles = secondaryFiles
  }

  def main(args: Array[String]): Unit = {
    println("Hello, world!")
  }

  def inputs(args: Seq[String]): String = {
    loadWdl(args.head) { namespace =>
      import wdl4s.wdl.types.WdlTypeJsonFormatter._
      namespace match {
        case x: WdlNamespaceWithWorkflow => x.workflow.inputs.toJson.prettyPrint
      }
    }
  }

  // When resolving non-http(s) files that do not actually exist locally, but instead in strings
  def resolveHttpAndSecondaryFiles(importString: String): WorkflowSource = {
    importString match {
      case s if s.startsWith("http://") || s.startsWith("https://") =>
        bridgeHelper.resolveUrl(s)
      case s =>
        bridgeHelper.resolveSecondaryPath(s, secondaryWdlFiles)
    }
  }

  // When resolving non-http(s) files that do exist locally
  def resolveHttpAndLocalFiles(importString: String): WorkflowSource = {
    importString match {
      case s if s.startsWith("http://") || s.startsWith("https://") =>
        bridgeHelper.resolveUrl(s)
      case s =>
        bridgeHelper.resolveLocalPath(basePath, s)
    }
  }

  private[this] def loadWdl(path: String)(f: WdlNamespace => String): String = {
    val pathFile = new JFile(path)
    val lines = scala.io.Source.fromFile(pathFile).mkString
    Try(WdlNamespaceWithWorkflow.load(lines, Seq(resolveHttpAndLocalFiles _)).get) match {
      case Success(namespace) => f(namespace)
      case Failure(t) =>
        println(t.getMessage)
        null
    }
  }

  def getInputFiles(file: JFile): util.Map[String, String] = {
    val lines = scala.io.Source.fromFile(file).mkString
    val ns = WdlNamespaceWithWorkflow.load(lines, Seq(resolveHttpAndLocalFiles _)).get

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

    val ns = WdlNamespaceWithWorkflow.load(lines, Seq(resolveHttpAndSecondaryFiles _)).get

    ns.imports foreach { imported =>
      importList.add(imported.uri)
    }

    importList
  }

  def getImportMap(file: JFile): util.LinkedHashMap[String, String] = {
    val lines = scala.io.Source.fromFile(file).mkString
    val importMap = new util.LinkedHashMap[String, String]()

    val ns = WdlNamespaceWithWorkflow.load(lines, Seq(resolveHttpAndSecondaryFiles _)).get

    ns.imports foreach { imported =>
      val importNamespace = imported.namespaceName
      if (!importNamespace.isEmpty) {
        importMap.put(importNamespace, imported.uri)
      }
    }

    importMap
  }


  def getOutputFiles(file: JFile): util.List[String] = {
    val lines = scala.io.Source.fromFile(file).mkString
    val ns = WdlNamespaceWithWorkflow.load(lines, Seq(resolveHttpAndLocalFiles _)).get

    val outputList = new util.ArrayList[String]()

    ns.workflow.outputs.seq foreach { value =>
      if (value.wdlType == WdlFileType || value.wdlType == WdlArrayType(WdlFileType)) {
        outputList.add(value.fullyQualifiedName)
      }
    }
    outputList
  }

  val passthrough: PartialFunction[WdlValue, WdlValue] = new PartialFunction[WdlValue, WdlValue] {
    def apply(x: WdlValue): WdlValue = x

    def isDefinedAt(x: WdlValue) = true
  }

  @throws(classOf[WdlParser.SyntaxError])
  def getCallsToDockerMap(file: JFile): util.LinkedHashMap[String, String] = {
    val lines = scala.io.Source.fromFile(file).mkString
    val ns = WdlNamespaceWithWorkflow.load(lines, Seq(resolveHttpAndSecondaryFiles _)).get
    val tasks = new util.LinkedHashMap[String, String]()

    ns.workflow.calls foreach { call =>
      if (ns.findTask(call.callable.fullyQualifiedName).nonEmpty) {
        ns.findTask(call.callable.fullyQualifiedName) foreach { task =>
          val dockerAttributes = task.runtimeAttributes.attrs.get("docker")
          tasks.put("dockstore_" + call.unqualifiedName, if (dockerAttributes.isDefined) dockerAttributes.get.collectAsSeq(passthrough).map(x => x.toWdlString.replaceAll("\"", "")).mkString("") else null)
        }
      } else {
        ns.namespaces.foreach { namespace =>
          if (namespace.findTask(call.unqualifiedName).nonEmpty) {
            namespace.findTask(call.unqualifiedName).foreach  { task =>
              val dockerAttributes = task.runtimeAttributes.attrs.get("docker")
              tasks.put("dockstore_" + call.unqualifiedName, if (dockerAttributes.isDefined) dockerAttributes.get.collectAsSeq(passthrough).map(x => x.toWdlString.replaceAll("\"", "")).mkString("") else null)
            }
          } else if (namespace.findTask(call.callable.fullyQualifiedName).nonEmpty) {
            namespace.findTask(call.callable.fullyQualifiedName).foreach  { task =>
              val dockerAttributes = task.runtimeAttributes.attrs.get("docker")
              tasks.put("dockstore_" + call.unqualifiedName, if (dockerAttributes.isDefined) dockerAttributes.get.collectAsSeq(passthrough).map(x => x.toWdlString.replaceAll("\"", "")).mkString("") else null)
            }
          } else if (namespace.findTask(call.callable.unqualifiedName).nonEmpty) {
            namespace.findTask(call.callable.unqualifiedName).foreach  { task =>
              val dockerAttributes = task.runtimeAttributes.attrs.get("docker")
              tasks.put("dockstore_" + call.unqualifiedName, if (dockerAttributes.isDefined) dockerAttributes.get.collectAsSeq(passthrough).map(x => x.toWdlString.replaceAll("\"", "")).mkString("") else null)
            }
          }
        }
      }
    }
    tasks
  }

  def getCallsToDependencies(file: JFile): util.LinkedHashMap[String, util.List[String]] = {
    val lines = scala.io.Source.fromFile(file).mkString
    val ns = WdlNamespaceWithWorkflow.load(lines, Seq(resolveHttpAndSecondaryFiles _)).get
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