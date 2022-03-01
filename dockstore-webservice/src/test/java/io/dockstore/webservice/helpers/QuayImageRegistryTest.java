package io.dockstore.webservice.helpers;

import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.Version;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;

public class QuayImageRegistryTest {

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();
    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();

    /**
     * Normally, we are only able to get 500 tags without using the paginated endpoint.
     * This tests that when there are over 500 tags, we can use the paginated endpoint to retrieve all of them instead.
     * Using calico/node because it has over 3838 tags
     */
    @Test
    public void getOver500TagsTest() {
        Token token = new Token();
        token.setContent("fakeQuayTokenBecauseWeDontReallyNeedOne");
        QuayImageRegistry quayImageRegistry = new QuayImageRegistry(token);
        Tool tool = new Tool();
        tool.setRegistry("quay.io");
        tool.setNamespace("calico");
        tool.setName("node");
        List<Tag> tags = quayImageRegistry.getTags(tool);
        int size = tags.size();
        Assert.assertTrue("Should be able to get more than the default 500 tags", size > 3838);
        tags.forEach(tag -> {
            Assert.assertNotEquals("Image ID should be populated", null, tag.getImageId());
            Assert.assertTrue("Images should be populated", tag.getImages().size() > 0);
            // If the tag size is null, that means at least one image with os/arch information was built and uploaded to Quay separately.
            if (tag.getSize() == null) {
                tag.getImages().stream().forEach(image -> {
                    boolean archOsInfoFilled = false;
                    if (image.getOs() != null || image.getArchitecture() != null) {
                        archOsInfoFilled = true;
                    }
                    Assert.assertTrue("The image's arch and/or os info should be filled in", archOsInfoFilled);
                });
            }
        });
        Set<String> collect = tags.parallelStream().map(Version::getName).collect(Collectors.toSet());
        int distinctSize = collect.size();
        Assert.assertEquals("There should be no tags with the same name", size, distinctSize);

        // This Quay repo has tags with > 1 manifest per image
        tool.setNamespace("openshift-release-dev");
        tool.setName("ocp-release");
        tags = quayImageRegistry.getTags(tool);
        Optional<Tag> tagWithMoreThanOneImage = tags.stream().filter(tag -> tag.getImages().size() > 1).findFirst();
        if (tagWithMoreThanOneImage.isEmpty()) {
            Assert.fail("There should be at least one tag where there is more than one image");
        }
        tags.forEach(tag -> {
            Assert.assertNotEquals("Image ID should be populated", null, tag.getImageId());
            Assert.assertTrue("Images should be populated", tag.getImages().size() > 0);
            // If the tag size is null, that means at least one image with os/arch information was built and uploaded to Quay separately.
            if (tag.getSize() == null) {
                tag.getImages().stream().forEach(image -> {
                    boolean archOsInfoFilled = false;
                    if (image.getOs() != null || image.getArchitecture() != null) {
                        archOsInfoFilled = true;
                    }
                    Assert.assertTrue("The image's arch and/or os info should be filled in", archOsInfoFilled);
                });
            }
        });
    }
}
