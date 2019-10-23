/*
 * Copyright 2019 OICR
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.dockstore.webservice.core.database;

import java.util.ArrayList;
import java.util.List;

/**
 * This class is only used to get data from the database in a more type-safe way
 * @author gluu
 * @since 1.8.0
 */
public class ToolPath {
    private final String registry;
    private final String namespace;
    private final String name;
    private final String toolname;

    public ToolPath(String registry, String namespace, String name, String toolname) {
        this.registry = registry;
        this.namespace = namespace;
        this.name = name;
        this.toolname = toolname;
    }

    public String getRegistry() {
        return registry;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getName() {
        return name;
    }

    public String getToolname() {
        return toolname;
    }

    public String getEntryPath() {
        List<String> segments = new ArrayList<>();
        segments.add(registry);
        segments.add(namespace);
        segments.add(name);
        if (toolname != null && !toolname.isEmpty()) {
            segments.add(toolname);
        }
        return String.join("/", segments);
    }

}
