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

package io.dockstore.webservice.jdbi;

import io.dockstore.common.Registry;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.ToolMode;
import io.dockstore.webservice.helpers.JsonLdRetriever;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;

import java.util.List;

/**
 * @author xliu
 */
public class ToolDAO extends EntryDAO<Tool> {
    public ToolDAO(SessionFactory factory) {
        super(factory);
    }

    public List<Tool> findByMode(final ToolMode mode) {
        return list(namedQuery("io.dockstore.webservice.core.Tool.findByMode").setParameter("mode", mode));
    }

    public List<Tool> findAllByPath(String path, boolean findPublished) {
        Object[] splitPath = Tool.splitPath(path, true);

        // Not a valid path
        if (splitPath == null) {
            return null;
        }

        // Valid path
        Registry registry = (Registry)splitPath[firstIndex];
        String namespace = (String)splitPath[secondIndex];
        String name = (String)splitPath[thirdIndex];

        // Create full query name
        String fullQueryName = "io.dockstore.webservice.core.Tool.";

        if (findPublished) {
            fullQueryName += "findPublishedByPath";
        } else {
            fullQueryName += "findByPath";
        }

        // Create query
        Query query = namedQuery(fullQueryName)
            .setParameter("registry", registry)
            .setParameter("namespace", namespace)
            .setParameter("name", name);

        return list(query);
    }

    public Tool findByPath(String path, boolean findPublished) {
        Object[] splitPath = Tool.splitPath(path, true);

        // Not a valid path
        if (splitPath == null) {
            return null;
        }

        // Valid path
        Registry registry = (Registry)splitPath[firstIndex];
        String namespace = (String)splitPath[secondIndex];
        String name = (String)splitPath[thirdIndex];
        String toolname = (String)splitPath[fourthIndex];


        // Create full query name
        String fullQueryName = "io.dockstore.webservice.core.Tool.";

        if (splitPath[fourthIndex] == null) {
            if (findPublished) {
                fullQueryName += "findPublishedByToolPathNullToolName";
            } else {
                fullQueryName += "findByToolPathNullToolName";
            }

        } else {
            if (findPublished) {
                fullQueryName += "findPublishedByToolPath";
            } else {
                fullQueryName += "findByToolPath";
            }
        }

        // Create query
        Query query = namedQuery(fullQueryName)
            .setParameter("registry", registry)
            .setParameter("namespace", namespace)
            .setParameter("name", name);

        if (splitPath[fourthIndex] != null) {
            query.setParameter("toolname", toolname);
        }

        return uniqueResult(query);
    }

    public List<Tool> findPublishedByNamespace(String namespace) {
        return list(namedQuery("io.dockstore.webservice.core.Tool.findPublishedByNamespace").setParameter("namespace", namespace));
    }
  
    /**
     * Return map containing schema.org info retrieved from the specified tool's descriptor cwl
     * @param id of specified tool
     * @return map containing schema.org info to be used as json-ld data
     */
    public List findPublishedSchemaById(long id) {
        Tool tool = findPublishedById(id);
        return JsonLdRetriever.getSchema(tool);
    }
}
