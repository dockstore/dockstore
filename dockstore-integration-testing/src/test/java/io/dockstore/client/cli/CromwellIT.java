package io.dockstore.client.cli;

import java.io.File;

import org.junit.Assert;
import org.junit.Test;

import com.google.common.collect.Lists;

import cromwell.Main;
import io.dockstore.client.Bridge;
import io.dropwizard.testing.ResourceHelpers;
import scala.collection.JavaConversions;
import scala.collection.immutable.List;

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
}
