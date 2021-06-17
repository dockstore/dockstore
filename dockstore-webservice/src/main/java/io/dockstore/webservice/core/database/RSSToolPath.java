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

import java.util.Date;

import io.dockstore.webservice.core.Tool;

/**
 * This class is only used to get data from the database in a more type-safe way
 * @author gluu
 * @since 1.8.0
 */
public class RSSToolPath {
    private final Tool tool = new Tool();

    public RSSToolPath(String registry, String namespace, String name, String entryName, Date lastUpdated, String description) {
        this.tool.setRegistry(registry);
        this.tool.setNamespace(namespace);
        this.tool.setName(name);
        this.tool.setToolname(entryName);
        this.tool.setLastUpdated(lastUpdated);
        this.tool.setDescription(description);
    }

    public Tool getTool() {
        return tool;
    }
}
