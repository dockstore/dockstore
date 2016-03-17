/*
 *    Copyright 2016 OICR
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

package io.dockstore.webservice.jdbi;

import java.util.List;

import org.hibernate.SessionFactory;

import io.dockstore.webservice.core.ToolMode;
import io.dockstore.webservice.core.Tool;

/**
 *
 * @author xliu
 */
public class ToolDAO extends EntryDAO<Tool> {
    public ToolDAO(SessionFactory factory) {
        super(factory);
    }

    public List<Tool> findByPath(String path) {
        return list(namedQuery("io.dockstore.webservice.core.Tool.findByPath").setParameter("path", path));
    }

    public Tool findByToolPath(String path, String tool) {
        return uniqueResult(namedQuery("io.dockstore.webservice.core.Tool.findByToolPath").setParameter("path", path).setParameter(
                "toolname", tool));
    }

    public List<Tool> findByMode(final ToolMode mode) {
        return list(namedQuery("io.dockstore.webservice.core.Tool.findByMode").setParameter("mode", mode));
    }

    public List<Tool> findPublishedByPath(String path) {
        return list(namedQuery("io.dockstore.webservice.core.Tool.findPublishedByPath").setParameter("path", path));
    }

    public Tool findPublishedByToolPath(String path, String tool) {
        return uniqueResult(namedQuery("io.dockstore.webservice.core.Tool.findPublishedByToolPath").setParameter("path", path)
                .setParameter("toolname", tool));
    }
}
