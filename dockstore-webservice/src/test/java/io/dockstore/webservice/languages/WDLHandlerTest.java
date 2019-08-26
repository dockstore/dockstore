package io.dockstore.webservice.languages;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;

import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.BioWorkflow;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.WorkflowVersion;
import io.dropwizard.testing.ResourceHelpers;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;

import static io.dockstore.webservice.languages.WDLHandler.ERROR_PARSING_WORKFLOW_YOU_MAY_HAVE_A_RECURSIVE_IMPORT;

public class WDLHandlerTest {

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();

    @Test
    public void getWorkflowContent() throws IOException {
        final WDLHandler wdlHandler = new WDLHandler();
        final Workflow workflow = new BioWorkflow();
        workflow.setAuthor("Jane Doe");
        workflow.setDescription("A good description");
        workflow.setEmail("janedoe@example.org");

        final String validFilePath = ResourceHelpers.resourceFilePath("valid_description_example.wdl");

        final String goodWdl = FileUtils.readFileToString(new File(validFilePath), StandardCharsets.UTF_8);
        wdlHandler.parseWorkflowContent(workflow, validFilePath, goodWdl, Collections.emptySet(), new WorkflowVersion());
        Assert.assertEquals(workflow.getAuthor(), "Mr. Foo");
        Assert.assertEquals(workflow.getEmail(), "foo@foo.com");
        Assert.assertEquals(workflow.getDescription(),
                "This is a cool workflow trying another line \n## This is a header\n* First Bullet\n* Second bullet");


        final String invalidFilePath = ResourceHelpers.resourceFilePath("invalid_description_example.wdl");
        final String invalidDescriptionWdl = FileUtils.readFileToString(new File(invalidFilePath), StandardCharsets.UTF_8);
        wdlHandler.parseWorkflowContent(workflow, invalidFilePath, invalidDescriptionWdl, Collections.emptySet(), new WorkflowVersion());
        Assert.assertNull(workflow.getAuthor());
        Assert.assertNull(workflow.getEmail());
    }

    @Test
    public void testRecursiveImports() throws IOException {
        final File recursiveWdl = new File(ResourceHelpers.resourceFilePath("recursive.wdl"));

        final WDLHandler wdlHandler = new WDLHandler();
        String s = FileUtils.readFileToString(recursiveWdl, StandardCharsets.UTF_8);
        try {
            wdlHandler.checkForRecursiveHTTPImports(s, new HashSet<>());
            Assert.fail("Should've detected recursive import");
        } catch (CustomWebApplicationException e) {
            Assert.assertEquals(ERROR_PARSING_WORKFLOW_YOU_MAY_HAVE_A_RECURSIVE_IMPORT, e.getErrorMessage());
        }

        final File notRecursiveWdl = new File(ResourceHelpers.resourceFilePath("valid_description_example.wdl"));
        s = FileUtils.readFileToString(notRecursiveWdl, StandardCharsets.UTF_8);
        wdlHandler.checkForRecursiveHTTPImports(s, new HashSet<>());
    }
}
