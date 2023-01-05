package io.dockstore.webservice.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import io.dockstore.webservice.core.Image;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.languages.LanguageHandlerInterface;
import io.dockstore.webservice.languages.LanguageHandlerInterface.DockerSpecifier;
import io.swagger.quay.client.ApiException;
import io.swagger.quay.client.model.QuayTag;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;
import uk.org.webcompere.systemstubs.stream.output.NoopStream;

@ExtendWith(SystemStubsExtension.class)
public class QuayImageRegistryTest {

    @SystemStub
    public final SystemOut systemOutRule = new SystemOut(new NoopStream());

    @SystemStub
    public final SystemErr systemErrRule = new SystemErr(new NoopStream());

    /**
     * Normally, we are only able to get 500 tags without using the paginated endpoint.
     * This tests that when there are over 500 tags, we can use the paginated endpoint to retrieve all of them instead.
     * Originally, this used calico/node with 3838 tags but it now has 8960 tags on 2022-12-19
     * Openshift-release-dev/ocp-release had 6737 on 2022-12-19
     * Slowing the test to 256.195 seconds.
     * Pointing the test at some of our repos reduces time to 5 seconds but is less thorough
     */
    @Test
    void getOver500TagsTest() {
        Token token = new Token();
        token.setContent("fakeQuayTokenBecauseWeDontReallyNeedOne");
        QuayImageRegistry quayImageRegistry = new QuayImageRegistry(token);
        Tool tool = new Tool();
        tool.setRegistry("quay.io");
        tool.setNamespace("dockstore");
        tool.setName("dockstore-webservice");
        List<Tag> tags = quayImageRegistry.getTags(tool);
        int size = tags.size();
        assertTrue(size > 500, "Should be able to get more than the default 500 tags");
        tags.forEach(tag -> {
            assertTrue(tag.getImages().size() > 0, "Images should be populated");
            assertTrue(!tag.getName().isEmpty() && tag.getImages().stream().noneMatch(img -> img.getImageUpdateDate().isEmpty()), "things look kinda sane");
            // If the tag size is null, that means at least one image with os/arch information was built and uploaded to Quay separately.
            if (tag.getSize() == null) {
                tag.getImages().forEach(this::checkImageArchOsInfo);
            }
        });
        Set<String> collect = tags.parallelStream().map(Version::getName).collect(Collectors.toSet());
        int distinctSize = collect.size();
        assertEquals(size, distinctSize, "There should be no tags with the same name");

        // This Quay repo has tags with > 1 manifest per image
        tool.setNamespace("dockstore");
        tool.setName("multi_manifest_test");
        tags = quayImageRegistry.getTags(tool);
        Optional<Tag> tagWithMoreThanOneImage = tags.stream().filter(tag -> tag.getImages().size() > 1).findFirst();
        if (tagWithMoreThanOneImage.isEmpty()) {
            fail("There should be at least one tag where there is more than one image");
        }
        tags.forEach(tag -> {
            assertTrue(tag.getImages().size() > 0, "Images should be populated");
            assertTrue(!tag.getName().isEmpty() && tag.getImages().stream().noneMatch(img -> img.getImageUpdateDate().isEmpty()), "things look kinda sane");
            // If the tag size is null, that means at least one image with os/arch information was built and uploaded to Quay separately.
            if (tag.getSize() == null) {
                tag.getImages().forEach(this::checkImageArchOsInfo);
            }
        });
    }

    @Test
    void testGetQuayTag() throws ApiException {
        final String repo = "calico/node";
        final String tag = "master";
        QuayImageRegistry quayImageRegistry = new QuayImageRegistry();
        Optional<QuayTag> quayTag = quayImageRegistry.getQuayTag(repo, tag);
        assertTrue(quayTag.isPresent());
        assertTrue(quayImageRegistry.isMultiArchImage(quayTag.get(), repo), "Should be a multi-arch image");
    }

    @Test
    void testHandleMultiArchTags() throws ApiException {
        final QuayImageRegistry quayImageRegistry = new QuayImageRegistry();
        String repo = "skopeo/stable";
        String tag = "latest"; // This is a multi-arch image built using the docker manifest method
        Optional<QuayTag> quayTag = quayImageRegistry.getQuayTag(repo, tag);
        assertTrue(quayTag.isPresent());
        assertTrue(quayImageRegistry.isMultiArchImage(quayTag.get(), repo), "Should be a multi-arch image");
        LanguageHandlerInterface.DockerSpecifier specifier = quayImageRegistry.getSpecifierFromTagName(quayTag.get().getName());
        assertEquals(DockerSpecifier.LATEST, specifier);
        List<QuayTag> quayTags = quayImageRegistry.getAllQuayTags(repo);
        List<QuayTag> cleanedQuayTagsList = new ArrayList<>(quayTags);
        Set<Image> images = quayImageRegistry.handleMultiArchQuayTags(repo, quayTag.get(), cleanedQuayTagsList, specifier);
        assertFalse(images.isEmpty());
        assertTrue(images.size() >= 4);
        images.forEach(this::checkImageArchOsInfo);
        // quayTags and cleanQuayTagsList should be the same size because the multi-arch image is built using buildx, so there's no individual images
        // for each architecture and there's no "cleaning" needed
        assertEquals(quayTags.size(), cleanedQuayTagsList.size());

        repo = "openshift-release-dev/ocp-release";
        tag = "4.12.0-0.nightly-multi-2022-08-22-124404"; // This is a multi-arch image built using the buildx method
        quayTag = quayImageRegistry.getQuayTag(repo, tag);
        assertTrue(quayTag.isPresent());
        assertTrue(quayImageRegistry.isMultiArchImage(quayTag.get(), repo), "Should be a multi-arch image");
        specifier = quayImageRegistry.getSpecifierFromTagName(quayTag.get().getName());
        assertEquals(DockerSpecifier.TAG, specifier);
        quayTags = quayImageRegistry.getAllQuayTags(repo);
        cleanedQuayTagsList = new ArrayList<>(quayTags);
        images = quayImageRegistry.handleMultiArchQuayTags(repo, quayTag.get(), cleanedQuayTagsList, specifier);
        assertFalse(images.isEmpty());
        assertTrue(images.size() >= 4);
        images.forEach(this::checkImageArchOsInfo);
        // quayTags and cleanQuayTagsList should be different sizes because the multi-arch image is built using the docker manifest method, so there's an individual image
        // for each architecture. These individuals images should be removed from cleanedQuayTagsList.
        assertNotEquals(quayTags.size(), cleanedQuayTagsList.size());
    }

    private void checkImageArchOsInfo(Image image) {
        assertTrue(image.getOs() != null || image.getArchitecture() != null, "The image's arch and/or os info should be filled in");
    }
}
