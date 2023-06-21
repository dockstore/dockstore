package io.dockstore.webservice.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

abstract class EntryTest {

    abstract Entry createEntry();

    @Test
    void testPublishingAndDeletability() {
        Entry entry = createEntry();
        assertFalse(entry.getIsPublished());
        assertFalse(entry.getWasEverPublic());
        assertTrue(entry.isDeletable());
        for (int i = 0; i < 3; i++) {
            entry.setIsPublished(true);
            assertTrue(entry.getIsPublished());
            assertTrue(entry.getWasEverPublic());
            assertFalse(entry.isDeletable());
            entry.setIsPublished(false);
            assertFalse(entry.getIsPublished());
            assertTrue(entry.getWasEverPublic());
            assertFalse(entry.isDeletable());
        }
    }
}
