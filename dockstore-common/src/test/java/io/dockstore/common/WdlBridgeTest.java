package io.dockstore.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.dropwizard.testing.FixtureHelpers;
import java.io.File;
import java.util.ArrayList;
import java.util.Map;
import org.junit.jupiter.api.Test;
import wdl.draft3.parser.WdlParser;
import wom.callable.ExecutableCallable;
import wom.executable.WomBundle;

class WdlBridgeTest {

    private static final String DOCKER_IMAGES_WDL_10 = FixtureHelpers.fixture("fixtures/dockerImages10.wdl");
    private static final String DOCKER_IMAGES_WDL_PRE_10 = FixtureHelpers.fixture("fixtures/dockerImagesPre10.wdl");

    @Test
    void testGetCallsToDockerMapWdl10() {
        checkDockerImageReferences(getDockerParameterMap(DOCKER_IMAGES_WDL_10));
    }

    @Test
    void testGetCallsToDockerMapWdlPre10() {
        checkDockerImageReferences(getDockerParameterMap(DOCKER_IMAGES_WDL_PRE_10));
    }

    private void checkDockerImageReferences(final Map<String, DockerParameter> callsToDockerMap) {
        callsToDockerMap.entrySet().stream().forEach(entry -> {
            if ("dockstore_parmeterizedDocker".equals(entry.getKey())) {
                assertEquals(DockerImageReference.DYNAMIC, entry.getValue().imageReference());
            } else {
                assertEquals(DockerImageReference.LITERAL, entry.getValue().imageReference());
            }
        });
    }

    private Map<String, DockerParameter> getDockerParameterMap(String filePath) {
        final WdlBridge wdlBridge = new WdlBridge();
        final WomBundle bundleFromContent = wdlBridge.getBundleFromContent(filePath, filePath, filePath);
        final ExecutableCallable executableCallable = wdlBridge.convertBundleToExecutableCallable(bundleFromContent);
        final Map<String, DockerParameter> callsToDockerMap = wdlBridge.getCallsToDockerMap(executableCallable);
        return callsToDockerMap;
    }

    @Test
    void testBooleanMetadata() throws WdlParser.SyntaxError {
        WdlBridge wdlBridge = new WdlBridge();
        File file = new File("src/test/resources/MitocondriaPipeline.wdl");
        String filePath = file.getAbsolutePath();
        String sourceFilePath = "/scripts/mitochondria_m2_wdl/MitochondriaPipeline.wdl";
        ArrayList<Map<String, String>> metadata = wdlBridge.getMetadata(filePath, sourceFilePath);
        // Known number of metadata objects
        final int knownMetadataObjectSize = 4;
        assertEquals(knownMetadataObjectSize, metadata.size(), "There should be 4 sets of metadata (3 from tasks, 1 from workflow)");
        assertTrue(metadata.get(0).containsValue("Removes alignment information while retaining recalibrated base qualities and original alignment tags"),
            "The metadata from a task should be gotten");
        assertTrue(metadata.get(2).containsValue("Takes in an hg38 bam or cram and outputs VCF of SNP/Indel calls on the mitochondria."),
            "The metadata from the main workflow should be gotten");
    }

    @Test
    void testBooleanMetadata2() throws WdlParser.SyntaxError {
        WdlBridge wdlBridge = new WdlBridge();
        File file = new File("src/test/resources/GvsExtractCallset.wdl");
        String filePath = file.getAbsolutePath();
        String sourceFilePath = "scripts/variantstore/wdl/GvsExtractCallset.wdl";
        ArrayList<Map<String, String>> metadata = wdlBridge.getMetadata(filePath, sourceFilePath);
        // Known number of metadata objects
        final int knownMetadataObjectSize = 0;
        assertEquals(knownMetadataObjectSize, metadata.size(), "There should be 0 sets of string value metadata (all metadata in this WDL is boolean only) ");
    }

}
