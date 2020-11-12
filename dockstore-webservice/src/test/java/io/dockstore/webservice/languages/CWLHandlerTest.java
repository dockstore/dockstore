package io.dockstore.webservice.languages;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import io.dockstore.common.Registry;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.FileFormat;
import io.dockstore.webservice.core.ParsedInformation;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.jdbi.ToolDAO;
import io.dropwizard.testing.ResourceHelpers;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import static org.mockito.Mockito.when;

/**
 * Tests public methods in the CWLHandler file
 * @author gluu
 * @since 1.5.0
 */
public class CWLHandlerTest {
    /**
     * Tests if the input and output file formats can be extracted from a CWL descriptor file
     * @throws Exception
     */
    @Test
    public void getInputFileFormats() throws Exception {
        CWLHandler cwlHandler = new CWLHandler();
        String filePath = ResourceHelpers.resourceFilePath("metadata_example4.cwl");
        Set<FileFormat> inputs = cwlHandler.getFileFormats(FileUtils.readFileToString(new File(filePath), StandardCharsets.UTF_8), "inputs");
        Assert.assertTrue(inputs.stream().anyMatch(input -> input.getValue().equals("http://edamontology.org/format_2572")));
        Set<FileFormat> outputs = cwlHandler.getFileFormats(FileUtils.readFileToString(new File(filePath), StandardCharsets.UTF_8), "outputs");
        Assert.assertTrue(outputs.stream().anyMatch(input -> input.getValue().equals("http://edamontology.org/format_1964")));
    }

    @Test
    public void testDeterminingImageRegistry() {
        CWLHandler cwlHandler = new CWLHandler();
        Assert.assertEquals("Should be Docker Hub", Registry.DOCKER_HUB, cwlHandler.determineImageRegistry("python:2.7").get());
        Assert.assertEquals("Should be Docker Hub", Registry.DOCKER_HUB, cwlHandler.determineImageRegistry("debian:jessie").get());
        Assert.assertEquals("Should be Docker Hub", Registry.DOCKER_HUB, cwlHandler.determineImageRegistry("knowengdev/data_cleanup_pipeline:07_11_2017").get());
        Assert.assertTrue("Should be empty for no version being included", cwlHandler.determineImageRegistry("knowengdev/data_cleanup_pipeline").isEmpty());
        Assert.assertTrue("Should be empty for no version being included", cwlHandler.determineImageRegistry("python:").isEmpty());
        Assert.assertEquals("Should be Amazon", Registry.AMAZON_ECR, cwlHandler.determineImageRegistry("137112412989.dkr.ecr.us-east-1.amazonaws.com/amazonlinux:latest").get());
        Assert.assertTrue("Should be empty, Google not supported yet", cwlHandler.determineImageRegistry("gcr.io/project-id/image:tag").isEmpty());
        Assert.assertEquals("Should be Quay", Registry.QUAY_IO, cwlHandler.determineImageRegistry("quay.io/ucsc_cgl/verifybamid:1.30.0").get());
    }

    @Test
    public void testURLHandler() {
        ParsedInformation parsedInformation = new ParsedInformation();
        CWLHandler.setImportsBasedOnMapValue(parsedInformation, "https://potato.com");
        Assert.assertTrue(parsedInformation.isHasHTTPImports());
        Assert.assertFalse(parsedInformation.isHasLocalImports());
        ParsedInformation parsedInformation2 = new ParsedInformation();
        CWLHandler.setImportsBasedOnMapValue(parsedInformation2, "http://potato.com");
        Assert.assertTrue(parsedInformation2.isHasHTTPImports());
        Assert.assertFalse(parsedInformation2.isHasLocalImports());
        ParsedInformation parsedInformation3 = new ParsedInformation();
        CWLHandler.setImportsBasedOnMapValue(parsedInformation3, "ftp://potato.com");
        Assert.assertFalse(parsedInformation3.isHasHTTPImports());
        Assert.assertTrue(parsedInformation3.isHasLocalImports());
        ParsedInformation parsedInformation4 = new ParsedInformation();
        CWLHandler.setImportsBasedOnMapValue(parsedInformation4, "potato.cwl");
        Assert.assertFalse(parsedInformation4.isHasHTTPImports());
        Assert.assertTrue(parsedInformation4.isHasLocalImports());
        ParsedInformation parsedInformation5 = new ParsedInformation();
        CWLHandler.setImportsBasedOnMapValue(parsedInformation5, "httppotato.cwl");
        Assert.assertFalse(parsedInformation5.isHasHTTPImports());
        Assert.assertTrue(parsedInformation5.isHasLocalImports());
    }

    @Test
    public void testURLFromEntry() {
        final LanguageHandlerInterface handler = Mockito.mock(LanguageHandlerInterface.class, Mockito.CALLS_REAL_METHODS);
        final ToolDAO toolDAO = Mockito.mock(ToolDAO.class);

        // Cases that don't rely on toolDAO
        Assert.assertNull(handler.getURLFromEntry("", toolDAO));
        Assert.assertEquals("https://images.sbgenomics.com/foo/bar", handler.getURLFromEntry("images.sbgenomics.com/foo/bar", toolDAO));
        Assert.assertEquals("https://images.sbgenomics.com/foo/bar", handler.getURLFromEntry("images.sbgenomics.com/foo/bar:1", toolDAO));
        Assert.assertEquals("https://hub.docker.com/_/foo", handler.getURLFromEntry("foo", toolDAO));
        Assert.assertEquals("https://hub.docker.com/_/foo", handler.getURLFromEntry("foo:1", toolDAO));

        // When toolDAO.findAllByPath() returns null/empty
        when(toolDAO.findAllByPath(Mockito.anyString(), Mockito.anyBoolean())).thenReturn(null);
        Assert.assertEquals("https://quay.io/repository/foo/bar", handler.getURLFromEntry("quay.io/foo/bar", toolDAO));
        Assert.assertEquals("https://quay.io/repository/foo/bar", handler.getURLFromEntry("quay.io/foo/bar:1", toolDAO));
        Assert.assertEquals("https://hub.docker.com/r/foo/bar", handler.getURLFromEntry("foo/bar", toolDAO));
        Assert.assertEquals("https://hub.docker.com/r/foo/bar", handler.getURLFromEntry("foo/bar:1", toolDAO));

        // When toolDAO.findAllByPath() returns non-empty List<Tool>
        when(toolDAO.findAllByPath(Mockito.anyString(), Mockito.anyBoolean())).thenReturn(List.of(Mockito.mock(Tool.class)));
        Assert.assertEquals("https://www.dockstore.org/containers/quay.io/foo/bar", handler.getURLFromEntry("quay.io/foo/bar", toolDAO));
        Assert.assertEquals("https://www.dockstore.org/containers/quay.io/foo/bar", handler.getURLFromEntry("quay.io/foo/bar:1", toolDAO));
        Assert.assertEquals("https://www.dockstore.org/containers/registry.hub.docker.com/foo/bar", handler.getURLFromEntry("foo/bar", toolDAO));
        Assert.assertEquals("https://www.dockstore.org/containers/registry.hub.docker.com/foo/bar", handler.getURLFromEntry("foo/bar:1", toolDAO));
    }

    @Test
    public void testGetContentWithMalformedDescriptors() throws IOException {
        CWLHandler cwlHandler = new CWLHandler();

        // create and mock parameters for getContent()
        final Set<SourceFile> emptySet = Collections.emptySet();
        final ToolDAO toolDAO = Mockito.mock(ToolDAO.class);
        when(toolDAO.findAllByPath(Mockito.anyString(), Mockito.anyBoolean())).thenReturn(null);

        // expect parsing error
        File cwlFile = new File(ResourceHelpers.resourceFilePath("brokenCWL.cwl"));
        try {
            cwlHandler.getContent("/brokenCWL.cwl", FileUtils.readFileToString(cwlFile, StandardCharsets.UTF_8), emptySet,
                LanguageHandlerInterface.Type.TOOLS, toolDAO);
        } catch (CustomWebApplicationException e) {
            Assert.assertEquals(e.getResponse().getStatus(), 400);
            Assert.assertTrue("Should contain parsing error statement, found: " + e.errorMessage,
                e.errorMessage.contains(CWLHandler.CWL_PARSE_ERROR));
        }

        // expect error based on invalid cwlVersion
        cwlFile = new File(ResourceHelpers.resourceFilePath("badVersionCWL.cwl"));
        try {
            cwlHandler.getContent("/badVersionCWL.cwl", FileUtils.readFileToString(cwlFile, StandardCharsets.UTF_8), emptySet,
                LanguageHandlerInterface.Type.TOOLS, toolDAO);
        } catch (CustomWebApplicationException e) {
            Assert.assertEquals(e.getResponse().getStatus(), 400);
            Assert.assertTrue("Should contain chosen cwlVersion error statement, found: " + e.errorMessage,
                e.errorMessage.contains(CWLHandler.CWL_VERSION_ERROR));
        }

        // expect error based on an undefined cwlVersion
        cwlFile = new File(ResourceHelpers.resourceFilePath("noVersionCWL.cwl"));
        try {
            cwlHandler.getContent("/noVersionCWL.cwl", FileUtils.readFileToString(cwlFile, StandardCharsets.UTF_8), emptySet,
                LanguageHandlerInterface.Type.TOOLS, toolDAO);
        } catch (CustomWebApplicationException e) {
            Assert.assertEquals(e.getResponse().getStatus(), 400);
            Assert.assertTrue("Should contain undefined cwlVersion error statement, found: " + e.errorMessage,
                e.errorMessage.contains(CWLHandler.CWL_NO_VERSION_ERROR));
        }
    }
}
