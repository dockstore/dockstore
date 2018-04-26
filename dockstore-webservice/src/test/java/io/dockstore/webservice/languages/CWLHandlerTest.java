package io.dockstore.webservice.languages;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import io.dockstore.webservice.core.FileFormat;
import io.dropwizard.testing.ResourceHelpers;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author gluu
 * @since 25/04/18
 */
public class CWLHandlerTest {
    @Test
    public void getInputFileFormats() throws Exception {
        CWLHandler cwlHandler = new CWLHandler();
        String filePath = ResourceHelpers.resourceFilePath("metadata_example4.cwl");
        Set<FileFormat> inputs = cwlHandler.getFileFormats(FileUtils.readFileToString(new File(filePath), StandardCharsets.UTF_8), "inputs");
        Assert.assertTrue(inputs.stream().anyMatch(input -> input.getValue().equals("http://edamontology.org/format_2572")));
        Set<FileFormat> outputs = cwlHandler.getFileFormats(FileUtils.readFileToString(new File(filePath), StandardCharsets.UTF_8), "outputs");
        Assert.assertTrue(outputs.stream().anyMatch(input -> input.getValue().equals("http://edamontology.org/format_1964")));
    }
}
