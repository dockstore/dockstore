package io.dockstore.client.cli;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import io.dropwizard.testing.ResourceHelpers;
import org.junit.Before;
import org.junit.BeforeClass;
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
        configFile = new File(ResourceHelpers.resourceFilePath("config"));
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
     */
    @Test
    public void testWorkflowMissingFilesToCopy() {
        File cwlFile = new File(ResourceHelpers.resourceFilePath("directory/1st-workflow.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("directory/1st-workflow-job.json"));
        this.baseWorkflowTest(cwlFile, cwlJSON, true, "workflow");
    }

    /**
     * This tests the same as testWorkflowMissingFilesToCopy except there are secondary files in the tool but none in the workflow
     */
    @Test
    public void testNullCase() {
        File cwlFile = new File(ResourceHelpers.resourceFilePath("directory/1st-workflow-no-secondary-in-workflow.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("directory/1st-workflow-job.json"));
        this.baseWorkflowTest(cwlFile, cwlJSON, true, "workflow");
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
