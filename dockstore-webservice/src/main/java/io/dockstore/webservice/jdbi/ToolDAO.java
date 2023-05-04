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

import io.dockstore.common.DescriptorLanguage;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.database.RSSToolPath;
import io.dockstore.webservice.core.database.ToolPath;
import io.dockstore.webservice.helpers.JsonLdRetriever;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.List;
import java.util.Optional;
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
        return this.currentSession().createNamedQuery("io.dockstore.webservice.core.Tool.findAllPublishedPaths", ToolPath.class).list();
    }

    public List<RSSToolPath> findAllPublishedPathsOrderByDbupdatedate() {
        return this.currentSession().createNamedQuery("io.dockstore.webservice.core.Tool.findAllPublishedPathsOrderByDbupdatedate", RSSToolPath.class).setMaxResults(RSS_ENTRY_LIMIT).list();
    }

    public List<String> getAllPublishedNamespaces() {
        return this.currentSession().createNamedQuery("io.dockstore.webservice.core.Tool.getPublishedNamespaces", String.class).list();
    }

    /**
     * Finds all tools with the given path (ignores tool name) When findPublished is true, will only look at published tools
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
     * Finds the tool matching the given tool path When findPublished is true, will only look at published tools
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
     *
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

    public List<Tool> findAllTools(int offset, int pageSize) {
        return namedTypedQuery("io.dockstore.webservice.core.Tool.findAllTools")
            .setMaxResults(pageSize)
            .setFirstResult(offset)
            .list();
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    protected Root<Tool> generatePredicate(DescriptorLanguage descriptorLanguage, String registry, String organization, String name, String toolname, String description, String author, Boolean checker,
        CriteriaBuilder cb, CriteriaQuery<?> q) {

        final Root<Tool> entryRoot = q.from(Tool.class);

        Predicate predicate = cb.isTrue(entryRoot.get("isPublished"));
        predicate = andLike(cb, predicate, entryRoot.get("namespace"), Optional.ofNullable(organization));
        predicate = andLike(cb, predicate, entryRoot.get("name"), Optional.ofNullable(name));
        predicate = andLike(cb, predicate, entryRoot.get("toolname"), Optional.ofNullable(toolname));
        predicate = andLike(cb, predicate, entryRoot.get("description"), Optional.ofNullable(description));
        predicate = andLike(cb, predicate, entryRoot.get("author"), Optional.ofNullable(author));
        if (checker != null && checker) {
            // tools are never checker workflows
            predicate = cb.isFalse(cb.literal(true));
        }

        if (descriptorLanguage != null) {
            // not quite right, this probably doesn't deal with tools that have both but https://hibernate.atlassian.net/browse/HHH-9991 is kicking my butt
            predicate = cb.and(predicate, cb.equal(entryRoot.get("descriptorType").as(String.class), descriptorLanguage.getShortName()));
        }
        predicate = andLike(cb, predicate, entryRoot.get("registry"), Optional.ofNullable(registry));

        q.where(predicate);
        return entryRoot;
    }
}
