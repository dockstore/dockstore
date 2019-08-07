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
package io.dockstore.client.cli;

import java.io.File;
import java.util.ArrayList;

import io.dropwizard.testing.ResourceHelpers;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;

/**
 * @author gluu
 * @since 02/08/17
 */
public class WorkflowInDirectoryTestIT {

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();

    private static File configFile;

    @BeforeClass
    public static void setup() {
        configFile = new File(ResourceHelpers.resourceFilePath("clientConfig"));
    }

    /**
     * Guard against failing tests killing VM
     */
    @Rule
    public final ExpectedSystemExit exit = ExpectedSystemExit.none();


    /**
     * This tests if the workflow could be ran with the client in a much different directory than the descriptor (not in the same directory as the descriptor)
     */
    @Test
    public void testWorkflowRunInDirectory() {
        File cwlFile = new File(ResourceHelpers.resourceFilePath("testDirectory2/1st-workflow.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("testDirectory2/1st-workflow-job.yml"));
        this.baseWorkflowTest(cwlFile, cwlJSON, false, "workflow");
    }

    /**
     * This tests whether cwltool can execute a workflow that contains an empty array hints property
     */
    @Ignore("cwltool 1.0.20190621234233 does not seem able to do this anymore")
    @Test
    public void testWorkflowWithEmptyHints() {
        File cwlFile = new File(ResourceHelpers.resourceFilePath("testDirectory2/1st-workflow-empty-hints.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("testDirectory2/1st-workflow-job.yml"));
        this.baseWorkflowTest(cwlFile, cwlJSON, true, "workflow");
    }

    /**
     * This tests secondary files that are denoted as a list of extensions (doesn't actually work, but we're at not dying horribly)
     */
    @Test
    public void testWorkflowRunInDirectorySecondaryFileExtensions() {
        File cwlFile = new File(ResourceHelpers.resourceFilePath("testDirectory2/1st-workflowArrayedOutput.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("testDirectory2/1st-workflow-jobArrayedOutput.json"));
        this.baseWorkflowTest(cwlFile, cwlJSON, false, "workflow");
    }

    /**
     * This tests secondary files that are denoted as a list of paths (doesn't actually work, but we're at not dying horribly)
     */
    @Test
    public void testWorkflowRunInDirectorySecondaryFileByPaths() {
        File cwlFile = new File(ResourceHelpers.resourceFilePath("testDirectory2/1st-workflowArrayedOutput.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("testDirectory2/1st-workflow-jobArrayedOutput2.json"));
        this.baseWorkflowTest(cwlFile, cwlJSON, false, "workflow");
    }

    /**
     * This tests 3 situations:
     * if using --script will ignore the missing test files when copying
     * if the workflow descriptor can successfully run if the tool descriptor has the secondary files instead of the workflow descriptor
     * the file id described in the parameter file is different than described in the tool descriptor even though they are the same
     * example: 1st-workflow-job.json says "reference__fasta__base", prep_samples_to_rec.cwl says "un_reference__fasta__base"
     * Tests if there are some secondary files in the workflow and some in the tool
     * This test is obsolete because cwltool from Dockstore 1.7.0 will not run without the secondary files present
     */
    @Test
    public void testWorkflowMissingFilesToCopy() {
        File cwlFile = new File(ResourceHelpers.resourceFilePath("directory/1st-workflow.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("directory/1st-workflow-job.json"));
        exit.expectSystemExitWithStatus(Client.IO_ERROR);
        this.baseWorkflowTest(cwlFile, cwlJSON, true, "workflow");
        systemErrRule.getLog().contains("Missing required secondary file");
    }

    /**
     * This tests the same as testWorkflowMissingFilesToCopy except there are secondary files in the tool but none in the workflow
     */
    @Test
    public void testNullCase() {
        File cwlFile = new File(ResourceHelpers.resourceFilePath("directory/1st-workflow-no-secondary-in-workflow.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("directory/1st-workflow-job.json"));
        exit.expectSystemExitWithStatus(3);
        this.baseWorkflowTest(cwlFile, cwlJSON, true, "workflow");
        systemErrRule.getLog().contains("Missing required secondary file");
    }

    @Test
    public void testJeltjeWorkflow() {
        File cwlFile = new File(ResourceHelpers.resourceFilePath("testDirectory3/workflow.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("testDirectory3/workflow.json"));
        this.baseWorkflowTest(cwlFile, cwlJSON, false, "workflow");
    }

    /**
     * This tests if the workflow could be ran with an input that is an array of array of files
     */
    @Test
    public void testArrayOfArrayOfInputs() {
        File cwlFile = new File(ResourceHelpers.resourceFilePath("arrayOfArrays/arrays.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("arrayOfArrays/testArrayLocalInputLocalOutput.json"));
        this.baseWorkflowTest(cwlFile, cwlJSON, false, "tool");
    }

    /**
     * This tests if the workflow could be ran with an input that is an array of array of files
     */
    @Test
    public void testArrayOfArrayOfInputsv1() {
        File cwlFile = new File(ResourceHelpers.resourceFilePath("arrayOfArrays/arraysv1.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("arrayOfArrays/testArrayLocalInputLocalOutput.json"));
        this.baseWorkflowTest(cwlFile, cwlJSON, false, "tool");
    }

    private void baseWorkflowTest(File descriptor, File testParameter, boolean script, String entryType) {
        ArrayList<String> args = new ArrayList<String>() {{
            add("--config");
            add(configFile.getPath());
            add(entryType);
            add("launch");
            add("--local-entry");
            add(descriptor.getPath());
            add("--yaml");
            add(testParameter.getPath());
            if (script) {
                add("--script");
            }
        }};
        Client.main(args.toArray(new String[args.size()]));
    }
}
