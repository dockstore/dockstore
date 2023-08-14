package io.dockstore.openapi.client.model;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Test some properties of the generated client classes that can make them easier to work with.
 */
public class ClientHierarchyTest {

    @Test
    public void testVersionHierarchy() {
        Tag tag = new Tag();
        WorkflowVersion workflowVersion = new WorkflowVersion();
        assertTrue(tag instanceof Version);
        assertTrue(workflowVersion instanceof Version);
    }

    @Test
    public void testEntryHierarchy() {
        Workflow workflow = new Workflow();
        BioWorkflow bioWorkflow = new BioWorkflow();
        DockstoreTool tool = new DockstoreTool();
        Notebook notebook = new Notebook();
        Service service = new Service();
        AppTool appTool = new AppTool();

        assertTrue(workflow instanceof Entry);
        assertTrue(bioWorkflow instanceof Entry);
        assertTrue(tool instanceof Entry);
        assertTrue(notebook instanceof Entry);
        assertTrue(service instanceof Entry);
        assertTrue(appTool instanceof Entry);

        assertTrue(workflow instanceof Workflow);
        assertTrue(bioWorkflow instanceof Workflow);
        assertTrue(notebook instanceof Workflow);
        assertTrue(service instanceof Workflow);
        assertTrue(appTool instanceof Workflow);
    }
}
