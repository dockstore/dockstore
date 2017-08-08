package io.dockstore.client.cli;

import java.io.File;
import java.util.ArrayList;

import io.dropwizard.testing.ResourceHelpers;
import org.junit.Before;
import org.junit.Test;

/**
 * @author gluu
 * @since 02/08/17
 */
public class WorkflowInDirectoryTestIT {

    private Client client;
    private File configFile;

    @Before
    public void setup() {
        client = new Client();
        configFile = new File(ResourceHelpers.resourceFilePath("config"));
    }

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
        client.main(args.toArray(new String[args.size()]));
    }

    @Test
    public void testWorkflowRunInDirectory2() {
        File cwlFile = new File(ResourceHelpers.resourceFilePath("directory/1st-workflow.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("directory/1st-workflow-job.json"));
        String synapseCWLFile = "/home/gluu/synapse/NA12878-platinum-chr20-workflow/main-NA12878-platinum-chr20.cwl";
        String synapseJSONFile = "/home/gluu/synapse/NA12878-platinum-chr20-workflow/main-NA12878-platinum-chr20-samples.json";
        ArrayList<String> args = new ArrayList<String>() {{
            add("--config");
            add(configFile.getAbsolutePath());
            add("workflow");
            add("launch");
            add("--local-entry");
            add(cwlFile.getAbsolutePath());
            //            add(synapseCWLFile);
            add("--yaml");
            add(cwlJSON.getAbsolutePath());
            //            add(synapseJSONFile);
        }};
        client.main(args.toArray(new String[args.size()]));
    }

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
