package io.dockstore.client.cli;

import java.io.File;
import java.util.ArrayList;

import io.dropwizard.testing.ResourceHelpers;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * @author gluu
 * @since 02/08/17
 */
public class WorkflowInDirectoryTestIT {

    private static Client client;
    private static File configFile;

    @BeforeClass
    public static void setup() {
        client = new Client();
        configFile = new File(ResourceHelpers.resourceFilePath("config"));
    }

    /**
     * This tests if the workflow could be ran with the client in a much different directory than the descriptor
     */
    @Test
    public void testWorkflowRunInDirectory() {
        File cwlFile = new File(ResourceHelpers.resourceFilePath("testDirectory2/1st-workflow.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("testDirectory2/1st-workflow-job.yml"));
        ArrayList<String> args = new ArrayList<String>() {{
            add("--config");
            add(configFile.getAbsolutePath());
            add("workflow");
            add("launch");
            add("--local-entry");
            add(cwlFile.getAbsolutePath());
            add("--yaml");
            add(cwlJSON.getAbsolutePath());
        }};
        this.client.main(args.toArray(new String[args.size()]));
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
        ArrayList<String> args = new ArrayList<String>() {{
            add("--config");
            add(configFile.getAbsolutePath());
            add("workflow");
            add("launch");
            add("--local-entry");
            add(cwlFile.getAbsolutePath());
            add("--yaml");
            add(cwlJSON.getAbsolutePath());
            add("--script");
        }};
        client.main(args.toArray(new String[args.size()]));
    }

    /**
     * This tests the same as testWorkflowMissingFilesToCopy except there are secondary files in the tool but none in the workflow
     */
    @Test
    public void testNullCase() {
        File cwlFile = new File(ResourceHelpers.resourceFilePath("directory/1st-workflow-no-secondary-in-workflow.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("directory/1st-workflow-job.json"));
        ArrayList<String> args = new ArrayList<String>() {{
            add("--config");
            add(configFile.getAbsolutePath());
            add("workflow");
            add("launch");
            add("--local-entry");
            add(cwlFile.getAbsolutePath());
            add("--yaml");
            add(cwlJSON.getAbsolutePath());
            add("--script");
        }};
        client.main(args.toArray(new String[args.size()]));
    }

    /**
     * This tests if the workflow could be ran with an input that is an array of array of files
     */
    @Test
    public void testArrayOfArrayOfInputs() {
        File cwlFile = new File(ResourceHelpers.resourceFilePath("arrayOfArrays/arrays.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("arrayOfArrays/testArrayLocalInputLocalOutput.json"));
        ArrayList<String> args = new ArrayList<String>() {{
            add("--config");
            add(configFile.getAbsolutePath());
            add("tool");
            add("launch");
            add("--local-entry");
            add(cwlFile.getAbsolutePath());
            add("--yaml");
            add(cwlJSON.getAbsolutePath());
        }};
        client.main(args.toArray(new String[args.size()]));
    }
}
