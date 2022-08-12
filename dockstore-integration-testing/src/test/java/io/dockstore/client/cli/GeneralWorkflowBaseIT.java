package io.dockstore.client.cli;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import io.swagger.client.api.ContainersApi;
import io.swagger.client.model.DockstoreTool;
import io.swagger.client.model.Image;
import io.swagger.client.model.Tag;
import java.util.List;

public class GeneralWorkflowBaseIT extends BaseIT {


    /**
     * Verifies that Checksums are saved.
     *
     * @param tags
     */
    protected static void verifyChecksumsAreSaved(List<Tag> tags) {
        assertTrue("The list of tags should not be empty", tags.size() > 0);
        for (Tag tag : tags) {
            List<Image> images = tag.getImages();
            assertTrue("Tag should have an image", images.size() > 0);
            String hashType = tag.getImages().get(0).getChecksums().get(0).getType();
            String checksum = tag.getImages().get(0).getChecksums().get(0).getChecksum();
            assertNotNull(hashType);
            assertFalse(hashType.isEmpty());
            assertNotNull(checksum);
            assertFalse(checksum.isEmpty());
        }
    }

    /**
     * Refreshes after deleting tag.
     *
     * @param toolApi
     * @param tool
     * @param tags
     */
    protected static void refreshAfterDeletedTag(ContainersApi toolApi, DockstoreTool tool, List<Tag> tags) {
        String imageID = tags.get(0).getImages().get(0).getImageID();

        final long count = testingPostgres.runSelectStatement("select count(*) from image", long.class);
        testingPostgres.runUpdateStatement("update image set image_id = 'dummyid'");
        assertEquals("dummyid", toolApi.getContainer(tool.getId(), null).getWorkflowVersions().get(0).getImages().get(0).getImageID());
        toolApi.refresh(tool.getId());
        final long count2 = testingPostgres.runSelectStatement("select count(*) from image", long.class);
        assertEquals(imageID, toolApi.getContainer(tool.getId(), null).getWorkflowVersions().get(0).getImages().get(0).getImageID());
        assertEquals(count, count2);
    }


}
