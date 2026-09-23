package io.dockstore.webservice.core;

class WorkflowTest extends EntryTest {

    @Override
    Entry createEntry() {
        return new BioWorkflow();
    }
}
