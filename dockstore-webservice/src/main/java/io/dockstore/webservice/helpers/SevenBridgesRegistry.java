package io.dockstore.webservice.helpers;

import io.dockstore.common.Registry;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.Tool;
import java.util.ArrayList;
import java.util.List;

/**
 * A no-op interface intended as a place-holder for where we will implement seven bridges functionality when they get around to exposing and
 * implementing their API.
 *
 * @author agduncan
 */
public class SevenBridgesRegistry extends AbstractImageRegistry {
    @Override
    public List<Tag> getTags(Tool tool) {
        return new ArrayList<>();
    }

    @Override
    public List<String> getNamespaces() {
        return new ArrayList<>();
    }

    @Override
    public List<Tool> getToolsFromNamespace(List<String> namespaces) {
        return new ArrayList<>();
    }

    @Override
    public void updateAPIToolsWithBuildInformation(List<Tool> apiTools) {
        for (Tool tool : apiTools) {
            tool.setRegistry(Registry.SEVEN_BRIDGES.getDockerPath());
        }
    }

    @Override
    public Registry getRegistry() {
        return Registry.SEVEN_BRIDGES;
    }

    @Override
    public boolean canConvertToAuto(Tool tool) {
        return false;
    }

    @Override
    public Tool getToolFromNamespaceAndRepo(final String organization, final String repository) {
        return unsupportedOperation();
    }
}
