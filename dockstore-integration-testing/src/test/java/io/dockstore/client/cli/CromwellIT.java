/*
 *    Copyright 2016 OICR
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

package io.dockstore.client.cli;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import cromwell.Main;
import io.dockstore.client.Bridge;
import io.dockstore.common.WDLFileProvisioning;
import io.dropwizard.testing.ResourceHelpers;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Test;
import scala.collection.JavaConversions;
import scala.collection.immutable.List;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;


/**
 * This tests integration with the CromWell engine and what will eventually be wdltool.
 * @author dyuen
 */
public class CromwellIT {

    @Test
    public void testWDL2Json() {
        File sourceFile = new File(ResourceHelpers.resourceFilePath("wdl.wdl"));
        final java.util.List<String> wdlDocuments = Lists.newArrayList(sourceFile.getAbsolutePath());
        final List<String> wdlList = JavaConversions.asScalaBuffer(wdlDocuments).toList();
        Bridge bridge = new Bridge();
        String inputs = bridge.inputs(wdlList);
        Assert.assertTrue(inputs.contains("three_step.cgrep.pattern"));
    }

    @Test
    public void runWDLWorkflow(){
        Main main = new Main();
        File workflowFile = new File(ResourceHelpers.resourceFilePath("wdl.wdl"));
        File parameterFile = new File(ResourceHelpers.resourceFilePath("wdl.json"));
        final java.util.List<String> wdlRun = Lists.newArrayList(workflowFile.getAbsolutePath(), parameterFile.getAbsolutePath());
        final List<String> wdlRunList = JavaConversions.asScalaBuffer(wdlRun).toList();
        // run a workflow
        final int run = main.run(wdlRunList);
        Assert.assertTrue(run == 0);
    }

    @Test
    public void fileProvisioning() {
        Main main = new Main();
        File workflowFile = new File(ResourceHelpers.resourceFilePath("wdlfileprov.wdl"));
        File parameterFile = new File(ResourceHelpers.resourceFilePath("wdlfileprov.json"));
        Bridge bridge = new Bridge();
        Map<String,String> wdlInputs = bridge.getInputFiles(workflowFile);

        WDLFileProvisioning wdlFileProvisioning = new WDLFileProvisioning(ResourceHelpers.resourceFilePath("config_file.txt"));
        Gson gson = new Gson();
        String jsonString;
        try {
            jsonString = FileUtils.readFileToString(parameterFile, StandardCharsets.UTF_8);
            Map<String, Object> inputJson = gson.fromJson(jsonString, HashMap.class);

            Map<String,Object> fileMap = wdlFileProvisioning.pullFiles(inputJson, wdlInputs);

            String newJsonPath = wdlFileProvisioning.createUpdatedInputsJson(inputJson, fileMap);
            final java.util.List<String> wdlRun = Lists.newArrayList(workflowFile.getAbsolutePath(), newJsonPath);
            final List<String> wdlRunList = JavaConversions.asScalaBuffer(wdlRun).toList();
            // run a workflow
            final int run = main.run(wdlRunList);
            Assert.assertTrue(run == 0);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testWDLResolver() {
        // If resolver works, this should throw no errors
        File sourceFile = new File(ResourceHelpers.resourceFilePath("wdl-sanger-workflow.wdl"));
        Bridge bridge = new Bridge();
        HashMap<String, String> secondaryFiles = new HashMap<>();
        secondaryFiles.put("wdl.wdl","task ps {\n" + "  command {\n" + "    ps\n" + "  }\n" + "  output {\n" + "    File procs = stdout()\n"
                + "  }\n" + "}\n" + "\n" + "task cgrep {\n" + "  String pattern\n" + "  File in_file\n" + "  command {\n"
                + "    grep '${pattern}' ${in_file} | wc -l\n" + "  }\n" + "  output {\n" + "    Int count = read_int(stdout())\n" + "  }\n"
                + "}\n" + "\n" + "task wc {\n" + "  File in_file\n" + "  command {\n" + "    cat ${in_file} | wc -l\n" + "  }\n"
                + "  output {\n" + "    Int count = read_int(stdout())\n" + "  }\n" + "}\n" + "\n" + "workflow three_step {\n"
                + "  call ps\n" + "  call cgrep {\n" + "    input: in_file=ps.procs\n" + "  }\n" + "  call wc {\n"
                + "    input: in_file=ps.procs\n" + "  }\n" + "}\n");
        bridge.setSecondaryFiles(secondaryFiles);
        bridge.getInputFiles(sourceFile);
    }
}
