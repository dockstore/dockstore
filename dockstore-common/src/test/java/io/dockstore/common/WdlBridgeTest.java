package io.dockstore.common;

import java.util.Map;

import io.dropwizard.testing.FixtureHelpers;
import org.junit.Assert;
import org.junit.Test;
import wom.callable.ExecutableCallable;
import wom.executable.WomBundle;

public class WdlBridgeTest {

    private static final String DOCKER_IMAGES_WDL_10 = FixtureHelpers.fixture("fixtures/dockerImages10.wdl");
    private static final String DOCKER_IMAGES_WDL_PRE_10 = FixtureHelpers.fixture("fixtures/dockerImagesPre10.wdl");

    @Test
    public void testGetCallsToDockerMapWdl10() {
        final Map<String, DockerParameter> callsToDockerMap = getDockerParameterMap(DOCKER_IMAGES_WDL_10);
        // See docker images in dockerImages10.wdl
        callsToDockerMap.entrySet().stream().forEach(entry -> {
            if ("dockstore_parmeterizedDocker".equals(entry.getKey())) {
                Assert.assertEquals(DockerImageReference.DYNAMIC, entry.getValue().imageReference());
            } else {
                Assert.assertEquals(DockerImageReference.LITERAL, entry.getValue().imageReference());
            }
        });
    }

    @Test
    public void testGetCallsToDockerMapWdlPre10() {
        final Map<String, DockerParameter> callsToDockerMap = getDockerParameterMap(DOCKER_IMAGES_WDL_PRE_10);
        // See docker images in dockerImages10.wdl
        callsToDockerMap.entrySet().stream().forEach(entry -> {
            if ("dockstore_parmeterizedDocker".equals(entry.getKey())) {
                Assert.assertEquals(DockerImageReference.DYNAMIC, entry.getValue().imageReference());
            } else {
                Assert.assertEquals(DockerImageReference.LITERAL, entry.getValue().imageReference());
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
