package io.dockstore.webservice.languages;

import java.util.Collections;

import io.dockstore.webservice.core.Workflow;
import org.junit.Assert;
import org.junit.Test;

public class WDLHandlerTest {

    private static final String GOOD_WDL =
            "workflow myWorkflow {\n"
                    + "    call myTask\n"
                    + "  meta {\n"
                    + "          author : \"Mr. Foo\"\n"
                    + "          email : \"foo@foo.com\"\n"
                    + "          description: \"Back to fixed!\"\n"
                    + "      }\n"
                    + "}\n"
                    + "\n"
                    + "task myTask {\n"
                    + "    command {\n"
                    + "        echo \"hello world\"\n"
                    + "    }\n"
                    + "    output {\n"
                    + "        String out = read_string(stdout())\n"
                    + "    }\n"
                    + "}";

    private static final String WDL_WITH_ILLEGAL_MULTILINE_DESCRIPTION =
            "workflow myWorkflow {\n"
                    + "    call myTask\n"
                    + "  meta {\n"
                    + "          author : \"Mr. Foo\"\n"
                    + "          email : \"foo@foo.com\"\n"
                    + "          description: \"This is a cool workflow trying another line \\n## This is a header\\n* First Bullet\\n* Second bullet\nIntentional problem\"\n"
                    + "      }\n"
                    + "}\n"
                    + "\n"
                    + "task myTask {\n"
                    + "    command {\n"
                    + "        echo \"hello world\"\n"
                    + "    }\n"
                    + "    output {\n"
                    + "        String out = read_string(stdout())\n"
                    + "    }\n"
                    + "}";

    @Test
    public void getWorkflowContent() {
        final WDLHandler wdlHandler = new WDLHandler();
        final Workflow workflow = new Workflow();
        workflow.setAuthor("Jane Doe");
        workflow.setDescription("A good description");
        workflow.setEmail("janedoe@example.org");

        wdlHandler.parseWorkflowContent(workflow, "/foo.wdl", GOOD_WDL, Collections.emptySet());
        Assert.assertEquals(workflow.getAuthor(), "Mr. Foo");
        Assert.assertEquals(workflow.getEmail(), "foo@foo.com");
        Assert.assertEquals(workflow.getDescription(), "Back to fixed!");

        wdlHandler.parseWorkflowContent(workflow, "/foo.wdl", WDL_WITH_ILLEGAL_MULTILINE_DESCRIPTION, Collections.emptySet());
        Assert.assertNull(workflow.getAuthor());
        Assert.assertNull(workflow.getEmail());
        Assert.assertNull(workflow.getDescription());


    }
}
