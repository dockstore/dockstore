/*
 *    Copyright 2017 OICR
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package io.dockstore.webservice.helpers;

import io.dockstore.common.Registry;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.Tool;
import java.util.ArrayList;
import java.util.List;

/**
 * A no-op interface intended as a place-holder for where we will implement docker hub functionality when they get around to exposing and
 * implementing their API.
 *
 * @author dyuen
 */
public class DockerHubRegistry extends AbstractImageRegistry {

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
            tool.setRegistry(Registry.DOCKER_HUB.getDockerPath());
        }
    }

    @Override
    public Registry getRegistry() {
        return Registry.DOCKER_HUB;
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
