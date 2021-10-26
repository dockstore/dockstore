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

import static io.dockstore.webservice.resources.MetadataResource.RSS_ENTRY_LIMIT;

import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.database.RSSToolPath;
import io.dockstore.webservice.core.database.ToolPath;
import io.dockstore.webservice.helpers.JsonLdRetriever;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;

/**
 * @author xliu
 */
public class ToolDAO extends EntryDAO<Tool> {
    public ToolDAO(SessionFactory factory) {
        super(factory);
    }

    public List<Tool> findByUserRegistryNamespace(final long userId, final String registry, final String namespace) {
        return list(namedTypedQuery("io.dockstore.webservice.core.Tool.findByUserRegistryNamespace").setParameter("userId", userId).setParameter("registry", registry).setParameter("namespace", namespace));
    }

    public List<Tool> findByUserRegistryNamespaceRepository(final long userId, final String registry, final String namespace, final String repository) {
        return list(namedTypedQuery("io.dockstore.webservice.core.Tool.findByUserRegistryNamespaceRepository").setParameter("userId", userId).setParameter("registry", registry).setParameter("namespace", namespace).setParameter("repository", repository));
    }

    public List<ToolPath> findAllPublishedPaths() {
        return list(this.currentSession().getNamedQuery("io.dockstore.webservice.core.Tool.findAllPublishedPaths"));
    }

    public List<RSSToolPath> findAllPublishedPathsOrderByDbupdatedate() {
        return list(this.currentSession().getNamedQuery("io.dockstore.webservice.core.Tool.findAllPublishedPathsOrderByDbupdatedate").setMaxResults(RSS_ENTRY_LIMIT));
    }

    /**
     * Finds all tools with the given path (ignores tool name)
     * When findPublished is true, will only look at published tools
     *
     * @param path
     * @param findPublished
     * @return A list of tools with the given path
     */
    public List<Tool> findAllByPath(String path, boolean findPublished) {
        String[] splitPath = Tool.splitPath(path, false);

        // Not a valid path
        if (splitPath == null) {
            return null;
        }

        // Valid path
        String registry = splitPath[registryIndex];
        String namespace = splitPath[orgIndex];
        String name = splitPath[repoIndex];

        // Create full query name
        String fullQueryName = "io.dockstore.webservice.core.Tool.";

        if (findPublished) {
            fullQueryName += "findPublishedByPath";
        } else {
            fullQueryName += "findByPath";
        }

        // Create query
        Query<Tool> toolQuery = namedTypedQuery(fullQueryName)
            .setParameter("registry", registry)
            .setParameter("namespace", namespace)
            .setParameter("name", name);

        return list(toolQuery);
    }

    /**
     * Finds the tool matching the given tool path
     * When findPublished is true, will only look at published tools
     *
     * @param path
     * @param findPublished
     * @return Tool matching the path
     */
    public Tool findByPath(String path, boolean findPublished) {
        final int minToolNamePathLength = 4; // <registry>/<org>/<repo>/<toolname>
        final int pathLength = path.split("/").length;
        // Determine which type of path to look for first: path with a tool name or path without a tool name
        boolean hasToolName = pathLength >= minToolNamePathLength;

        Tool result = findByPath(path, hasToolName, findPublished);
        if (pathLength >= minToolNamePathLength && result == null) {
            // If <repo> contains slashes, there are two scenarios that can form the same tool path. In the following scenarios, assume that <registry> and <org> are the same.
            // Scenario 1: <repo> = 'foo', <toolname> = 'bar'
            // Scenario 2: <repo> = 'foo/bar', <toolname> = NULL
            // Need to try the opposite scenario if we couldn't find the tool using the initial scenario (i.e. if we first tried to find a path with a toolname, try to find one without).
            result = findByPath(path, !hasToolName, findPublished);
        }

        return result;
    }

    /**
     * Finds the tool matching the given tool path
     * When findPublished is true, will only look at published tools
     * When hasToolName is true, will assume that the path contains a tool name when splitting the path
     *
     * @param path
     * @param hasToolName Boolean indicating whether the path contains a tool name. This is used when splitting the path.
     * @param findPublished
     * @return Tool matching the path
     */
    public Tool findByPath(String path, boolean hasToolName, boolean findPublished) {
        String[] splitPath = Tool.splitPath(path, hasToolName);

        // Not a valid path
        if (splitPath == null) {
            return null;
        }

        // Valid path
        String registry = splitPath[registryIndex];
        String namespace = splitPath[orgIndex];
        String name = splitPath[repoIndex];
        String toolname = splitPath[entryNameIndex];


        // Create full query name
        String fullQueryName = "io.dockstore.webservice.core.Tool.";

        if (splitPath[entryNameIndex] == null) {
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
        Query<Tool> query = namedTypedQuery(fullQueryName)
            .setParameter("registry", registry)
            .setParameter("namespace", namespace)
            .setParameter("name", name);

        if (splitPath[entryNameIndex] != null) {
            query.setParameter("toolname", toolname);
        }

        return uniqueResult(query);
    }

    public List<Tool> findPublishedByNamespace(String namespace) {
        return list(namedTypedQuery("io.dockstore.webservice.core.Tool.findPublishedByNamespace").setParameter("namespace", namespace));
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

    public Tool findByAlias(String alias) {
        return uniqueResult(namedTypedQuery("io.dockstore.webservice.core.Tool.getByAlias").setParameter("alias", alias));
    }
}
