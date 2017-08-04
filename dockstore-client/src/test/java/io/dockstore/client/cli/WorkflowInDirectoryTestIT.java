package io.dockstore.client.cli;

import java.io.File;
import java.util.ArrayList;

import io.dropwizard.testing.ResourceHelpers;
import org.junit.Test;

/**
 * @author gluu
 * @since 02/08/17
 */
public class WorkflowInDirectoryTestIT {
    @Test
    public void testWorkflowRunInDirectory() {
        Client client = new Client();
        File cwlFile = new File(ResourceHelpers.resourceFilePath("testDirectory2/1st-workflow.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("testDirectory2/1st-workflow-job.yml"));
        File configFile = new File(ResourceHelpers.resourceFilePath("config"));
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
        Client client = new Client();
        File cwlFile = new File(ResourceHelpers.resourceFilePath("directory/1st-workflow.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("directory/1st-workflow-job.json"));
        File configFile = new File(ResourceHelpers.resourceFilePath("config"));
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
}
