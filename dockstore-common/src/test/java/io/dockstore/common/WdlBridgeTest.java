package io.dockstore.common;

import java.util.LinkedHashMap;

import io.dropwizard.testing.FixtureHelpers;
import org.junit.Test;
import wom.callable.ExecutableCallable;
import wom.executable.WomBundle;

public class WdlBridgeTest {

    private static final String DOCKER_IMAGES_WDL = FixtureHelpers.fixture("fixtures/dockerImages.wdl");

    @Test
    public void testGetCallsToDockerMap() {
        final WdlBridge wdlBridge = new WdlBridge();
        final String filePath = "/dockerImages.wdl"; // Doesn't really matter
        final WomBundle bundleFromContent = wdlBridge.getBundleFromContent(DOCKER_IMAGES_WDL, filePath, filePath);
        final ExecutableCallable executableCallable = wdlBridge.convertBundleToExecutableCallable(bundleFromContent);
        final LinkedHashMap<String, String> callsToDockerMap = wdlBridge.getCallsToDockerMap(executableCallable);
        System.out.println(callsToDockerMap.toString());
    }
}
