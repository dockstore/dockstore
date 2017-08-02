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
        File cwlFile = new File(ResourceHelpers.resourceFilePath("workflow_in_directory/1st-workflow.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("workflow_in_directory/1st-workflow-job.yml"));
        ArrayList<String> args = new ArrayList<String>() {{
            add("workflow");
            add("launch");
            add("--local-entry");
            add(cwlFile.getAbsolutePath());
            add("--yaml");
            add(cwlJSON.getAbsolutePath());
        }};
        client.main(args.toArray(new String[args.size()]));
    }
}
