package io.dockstore.webservice.core;

class NotebookTest extends EntryTest {

    @Override
    Entry createEntry() {
        return new Notebook();
    }
}
