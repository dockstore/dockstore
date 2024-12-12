package io.dockstore.webservice.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.dockstore.webservice.core.BioWorkflow;
import io.dockstore.webservice.core.Notebook;
import org.junit.jupiter.api.Test;

class AliasHelperTest {

    @Test
    void testCreateWorkflowVersionAliasUrl() {
        assertEquals("https://dockstore.url/aliases/workflow-versions/abc123",
            AliasHelper.createWorkflowVersionAliasUrl("https://dockstore.url", new BioWorkflow(), "abc123"));
        assertEquals("https://dockstore.url/aliases/notebook-versions/foobar",
            AliasHelper.createWorkflowVersionAliasUrl("https://dockstore.url", new Notebook(), "foobar"));
    }
}
