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

import java.util.ArrayList;
import java.util.List;

import io.dockstore.common.Registry;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.Tool;

/**
 * Created by aduncan on 09/12/16.
 */
public class ManualRegistry extends AbstractImageRegistry {
    private final Registry registry;

    public ManualRegistry(Registry registry) {
        this.registry = registry;
    }

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
//        for (Tool tool : apiTools) {
//            tool.setRegistry(registry.toString());
//        }
    }

    @Override
    public Registry getRegistry() {
        return registry;
    }
}
