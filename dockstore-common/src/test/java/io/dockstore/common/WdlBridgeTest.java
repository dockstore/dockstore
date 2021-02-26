package io.dockstore.common;

import java.util.Map;

import io.dropwizard.testing.FixtureHelpers;
import org.junit.Test;
import wom.callable.ExecutableCallable;
import wom.executable.WomBundle;

import static org.junit.Assert.assertEquals;

public class WdlBridgeTest {

    private static final String DOCKER_IMAGES_WDL_10 = FixtureHelpers.fixture("fixtures/dockerImages10.wdl");
    private static final String DOCKER_IMAGES_WDL_PRE_10 = FixtureHelpers.fixture("fixtures/dockerImagesPre10.wdl");

    @Test
    public void testGetCallsToDockerMapWdl10() {
        checkDockerImageReferences(getDockerParameterMap(DOCKER_IMAGES_WDL_10));
    }

    @Test
    public void testGetCallsToDockerMapWdlPre10() {
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
}
