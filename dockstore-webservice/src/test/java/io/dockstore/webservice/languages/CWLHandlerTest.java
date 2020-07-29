package io.dockstore.webservice.languages;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import io.dockstore.common.Registry;
import io.dockstore.webservice.core.FileFormat;
import io.dockstore.webservice.core.ParsedInformation;
import io.dropwizard.testing.ResourceHelpers;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Test;

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
    }
}
