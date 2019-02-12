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

import java.util.ArrayList;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import io.dockstore.webservice.core.SourceControlConverter;
import io.dockstore.webservice.core.Workflow;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;

/**
 * @author dyuen
 */
public class WorkflowDAO extends EntryDAO<Workflow> {

    private static final String WORKFLOW_NAME = "workflowName";
    private static final String IS_PUBLISHED = "isPublished";

    public WorkflowDAO(SessionFactory factory) {
        super(factory);
    }

    /**
     * Finds all workflows with the given path (ignores workflow name)
     * When findPublished is true, will only look at published workflows
     *
     * @param path
     * @param findPublished
     * @return A list of workflows with the given path
     */
    public List<Workflow> findAllByPath(String path, boolean findPublished) {
        String[] splitPath = Workflow.splitPath(path);

        // Not a valid path
        if (splitPath == null) {
            return null;
        }

        // Valid path
        String sourcecontrol = splitPath[registryIndex];
        String organization = splitPath[orgIndex];
        String repository = splitPath[repoIndex];

        // Create full query name
        String fullQueryName = "io.dockstore.webservice.core.Workflow.";

        if (findPublished) {
            fullQueryName += "findPublishedByPath";
        } else {
            fullQueryName += "findByPath";
        }

        SourceControlConverter converter = new SourceControlConverter();
        // Create query
        Query query = namedQuery(fullQueryName)
            .setParameter("sourcecontrol", converter.convertToEntityAttribute(sourcecontrol))
            .setParameter("organization", organization)
            .setParameter("repository", repository);

        return list(query);
    }

    /**
     * Finds the workflow matching the given workflow path
     * When findPublished is true, will only look at published workflows
     *
     * @param path
     * @param findPublished
     * @return Workflow matching the path
     */
    public Workflow findByPath(String path, boolean findPublished) {
        String[] splitPath = Workflow.splitPath(path);

        // Not a valid path
        if (splitPath == null) {
            return null;
        }

        // Valid path
        String sourcecontrol = splitPath[registryIndex];
        String organization = splitPath[orgIndex];
        String repository = splitPath[repoIndex];
        String workflowname = splitPath[entryNameIndex];


        // Create full query name
        String fullQueryName = "io.dockstore.webservice.core.Workflow.";

        if (splitPath[entryNameIndex] == null) {
            if (findPublished) {
                fullQueryName += "findPublishedByWorkflowPathNullWorkflowName";
            } else {
                fullQueryName += "findByWorkflowPathNullWorkflowName";
            }

        } else {
            if (findPublished) {
                fullQueryName += "findPublishedByWorkflowPath";
            } else {
                fullQueryName += "findByWorkflowPath";
            }
        }

        SourceControlConverter converter = new SourceControlConverter();

        // Create query
        Query query = namedQuery(fullQueryName)
            .setParameter("sourcecontrol", converter.convertToEntityAttribute(sourcecontrol))
            .setParameter("organization", organization)
            .setParameter("repository", repository);

        if (splitPath[entryNameIndex] != null) {
            query.setParameter("workflowname", workflowname);
        }

        return uniqueResult(query);
    }

    public List<Workflow> findByPaths(List<String> paths, boolean findPublished) {
        List<Predicate> predicates = new ArrayList<>();
        SourceControlConverter converter = new SourceControlConverter();

        // Create dynamic query using a CriteriaBuilder instance for all paths in the given list of strings
        CriteriaBuilder cb = currentSession().getCriteriaBuilder();
        CriteriaQuery<Workflow> q = cb.createQuery(Workflow.class);
        Root<Workflow> entry = q.from(Workflow.class);

        for (String path : paths) {
            String[] splitPath = Workflow.splitPath(path);

            if (splitPath == null) {
                continue;
            }

            String sourcecontrol = splitPath[registryIndex];
            String organization = splitPath[orgIndex];
            String repository = splitPath[repoIndex];
            String workflowname = splitPath[entryNameIndex];

            Predicate predicate;
            if (splitPath[entryNameIndex] == null) {
                if (findPublished) {
                    predicate = cb.and(
                            cb.isNull(entry.get(WORKFLOW_NAME)),
                            cb.isTrue(entry.get(IS_PUBLISHED))
                    );
                } else {
                    predicate = cb.isNull(entry.get(WORKFLOW_NAME));
                }
            } else {
                if (findPublished) {
                    predicate = cb.and(
                            cb.equal(entry.get(WORKFLOW_NAME), workflowname),
                            cb.isTrue(entry.get(IS_PUBLISHED))
                    );
                } else {
                    predicate = cb.equal(entry.get(WORKFLOW_NAME), workflowname);
                }
            }

            predicates.add(cb.and(
                    cb.equal(entry.get("sourceControl"), converter.convertToEntityAttribute(sourcecontrol)),
                    cb.equal(entry.get("organization"), organization),
                    cb.equal(entry.get("repository"), repository),
                    predicate)
            );
        }
        // Perform disjunctive OR over all predicates in the array
        q.where(cb.or(predicates.toArray(new Predicate[]{})));

        EntityManager entityManager = currentSession().getEntityManagerFactory().createEntityManager();
        return entityManager.createQuery(q).getResultList();
    }

    public List<Workflow> findByGitUrl(String giturl) {
        return list(namedQuery("io.dockstore.webservice.core.Workflow.findByGitUrl")
            .setParameter("gitUrl", giturl));
    }

    public List<Workflow> findPublishedByOrganization(String organization) {
        return list(namedQuery("io.dockstore.webservice.core.Workflow.findPublishedByOrganization")
            .setParameter("organization", organization));
    }
}
