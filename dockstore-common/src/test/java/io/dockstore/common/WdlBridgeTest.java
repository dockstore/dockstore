package io.dockstore.common;

import java.util.Map;

import io.dropwizard.testing.FixtureHelpers;
import org.junit.Assert;
import org.junit.Test;
import wom.callable.ExecutableCallable;
import wom.executable.WomBundle;

public class WdlBridgeTest {

    private static final String DOCKER_10_IMAGES_WDL = FixtureHelpers.fixture("fixtures/dockerImages10.wdl");

    @Test
    public void testGetCallsToDockerMapWdl10() {
        final Map<String, DockerParameter> callsToDockerMap = getDockerParameterMap("/dockerImanges10.wdl");
        // See docker images in dockerImages10.wdl
        Assert.assertTrue(callsToDockerMap.get("dockstore_latestDocker").literal());
        Assert.assertTrue(callsToDockerMap.get("dockstore_taglessDocker").literal());
        Assert.assertFalse(callsToDockerMap.get("dockstore_parmeterizedDocker").literal());
        Assert.assertTrue(callsToDockerMap.get("dockstore_versionedDocker").literal());
    }

    private Map<String, DockerParameter> getDockerParameterMap(String filePath) {
        final WdlBridge wdlBridge = new WdlBridge();
        final WomBundle bundleFromContent = wdlBridge.getBundleFromContent(DOCKER_10_IMAGES_WDL, filePath, filePath);
        final ExecutableCallable executableCallable = wdlBridge.convertBundleToExecutableCallable(bundleFromContent);
        final Map<String, DockerParameter> callsToDockerMap = wdlBridge.getCallsToDockerMap(executableCallable);
        return callsToDockerMap;
    }
}
