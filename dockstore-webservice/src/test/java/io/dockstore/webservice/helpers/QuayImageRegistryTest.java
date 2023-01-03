package io.dockstore.webservice.helpers;

import io.dockstore.webservice.core.Image;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.languages.LanguageHandlerInterface;
import io.swagger.quay.client.ApiException;
import io.swagger.quay.client.model.QuayTag;
import java.util.ArrayList;
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
     * Originally, this used calico/node with 3838 tags but it now has 8960 tags on 2022-12-19
     * Openshift-release-dev/ocp-release had 6737 on 2022-12-19
     * Slowing the test to 256.195 seconds.
     * Pointing the test at some of our repos reduces time to 5 seconds but is less thorough
     */
    @Test
    public void getOver500TagsTest() {
        Token token = new Token();
        token.setContent("fakeQuayTokenBecauseWeDontReallyNeedOne");
        QuayImageRegistry quayImageRegistry = new QuayImageRegistry(token);
        Tool tool = new Tool();
        tool.setRegistry("quay.io");
        tool.setNamespace("dockstore");
        tool.setName("dockstore-webservice");
        List<Tag> tags = quayImageRegistry.getTags(tool);
        int size = tags.size();
        Assert.assertTrue("Should be able to get more than the default 500 tags", size > 500);
        tags.forEach(tag -> {
            Assert.assertTrue("Images should be populated", tag.getImages().size() > 0);
            Assert.assertTrue("things look kinda sane", !tag.getName().isEmpty() && tag.getImages().stream().noneMatch(img -> img.getImageUpdateDate().isEmpty()));
            // If the tag size is null, that means at least one image with os/arch information was built and uploaded to Quay separately.
            if (tag.getSize() == null) {
                tag.getImages().forEach(this::checkImageArchOsInfo);
            }
        });
        Set<String> collect = tags.parallelStream().map(Version::getName).collect(Collectors.toSet());
        int distinctSize = collect.size();
        Assert.assertEquals("There should be no tags with the same name", size, distinctSize);

        // This Quay repo has tags with > 1 manifest per image
        tool.setNamespace("dockstore");
        tool.setName("multi_manifest_test");
        tags = quayImageRegistry.getTags(tool);
        Optional<Tag> tagWithMoreThanOneImage = tags.stream().filter(tag -> tag.getImages().size() > 1).findFirst();
        if (tagWithMoreThanOneImage.isEmpty()) {
            Assert.fail("There should be at least one tag where there is more than one image");
        }
        tags.forEach(tag -> {
            Assert.assertTrue("Images should be populated", tag.getImages().size() > 0);
            Assert.assertTrue("things look kinda sane", !tag.getName().isEmpty() && tag.getImages().stream().noneMatch(img -> img.getImageUpdateDate().isEmpty()));
            // If the tag size is null, that means at least one image with os/arch information was built and uploaded to Quay separately.
            if (tag.getSize() == null) {
                tag.getImages().forEach(this::checkImageArchOsInfo);
            }
        });
    }

    @Test
    public void testGetQuayTag() throws ApiException {
        final String repo = "calico/node";
        final String tag = "master";
        QuayImageRegistry quayImageRegistry = new QuayImageRegistry();
        Optional<QuayTag> quayTag = quayImageRegistry.getQuayTag(repo, tag);
        Assert.assertTrue(quayTag.isPresent());
        Assert.assertTrue("Should be a multi-arch image", quayImageRegistry.isMultiArchImage(quayTag.get(), repo));
    }

    @Test
    public void testHandleMultiArchTags() throws ApiException {
        final QuayImageRegistry quayImageRegistry = new QuayImageRegistry();
        String repo = "skopeo/stable";
        String tag = "latest"; // This is a multi-arch image built using the docker manifest method
        Optional<QuayTag> quayTag = quayImageRegistry.getQuayTag(repo, tag);
        Assert.assertTrue(quayTag.isPresent());
        Assert.assertTrue("Should be a multi-arch image", quayImageRegistry.isMultiArchImage(quayTag.get(), repo));
        LanguageHandlerInterface.DockerSpecifier specifier = quayImageRegistry.getSpecifierFromTagName(quayTag.get().getName());
        Assert.assertEquals(LanguageHandlerInterface.DockerSpecifier.LATEST, specifier);
        List<QuayTag> quayTags = quayImageRegistry.getAllQuayTags(repo);
        List<QuayTag> cleanedQuayTagsList = new ArrayList<>(quayTags);
        Set<Image> images = quayImageRegistry.handleMultiArchQuayTags(repo, quayTag.get(), cleanedQuayTagsList, specifier);
        Assert.assertFalse(images.isEmpty());
        Assert.assertTrue(images.size() >= 4);
        images.forEach(this::checkImageArchOsInfo);
        // quayTags and cleanQuayTagsList should be the same size because the multi-arch image is built using buildx, so there's no individual images
        // for each architecture and there's no "cleaning" needed
        Assert.assertEquals(quayTags.size(), cleanedQuayTagsList.size());

        repo = "openshift-release-dev/ocp-release";
        tag = "4.12.0-0.nightly-multi-2022-08-22-124404"; // This is a multi-arch image built using the buildx method
        quayTag = quayImageRegistry.getQuayTag(repo, tag);
        Assert.assertTrue(quayTag.isPresent());
        Assert.assertTrue("Should be a multi-arch image", quayImageRegistry.isMultiArchImage(quayTag.get(), repo));
        specifier = quayImageRegistry.getSpecifierFromTagName(quayTag.get().getName());
        Assert.assertEquals(LanguageHandlerInterface.DockerSpecifier.TAG, specifier);
        quayTags = quayImageRegistry.getAllQuayTags(repo);
        cleanedQuayTagsList = new ArrayList<>(quayTags);
        images = quayImageRegistry.handleMultiArchQuayTags(repo, quayTag.get(), cleanedQuayTagsList, specifier);
        Assert.assertFalse(images.isEmpty());
        Assert.assertTrue(images.size() >= 4);
        images.forEach(this::checkImageArchOsInfo);
        // quayTags and cleanQuayTagsList should be different sizes because the multi-arch image is built using the docker manifest method, so there's an individual image
        // for each architecture. These individuals images should be removed from cleanedQuayTagsList.
        Assert.assertNotEquals(quayTags.size(), cleanedQuayTagsList.size());
    }

    private void checkImageArchOsInfo(Image image) {
        Assert.assertTrue("The image's arch and/or os info should be filled in", image.getOs() != null || image.getArchitecture() != null);
    }
}
