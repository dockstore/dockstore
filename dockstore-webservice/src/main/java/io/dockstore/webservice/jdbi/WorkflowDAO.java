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

import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.SourceControl;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.Service;
import io.dockstore.webservice.core.SourceControlConverter;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.Workflow;
import jakarta.persistence.NoResultException;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.http.HttpStatus;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author dyuen
 */
public class WorkflowDAO extends EntryDAO<Workflow> {

    private static final String WORKFLOW_NAME = "workflowName";
    private static final String IS_PUBLISHED = "isPublished";
    private static final Logger LOG = LoggerFactory.getLogger(WorkflowDAO.class);

    public WorkflowDAO(SessionFactory factory) {
        super(factory);
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    @Override
    protected Root<Workflow> generatePredicate(DescriptorLanguage descriptorLanguage, String registry, String organization, String name, String toolname, String description, String author, Boolean checker, CriteriaBuilder cb, CriteriaQuery<?> q) {
        throw new UnsupportedOperationException("Only supported for BioWorkflow and Tools");
    }

    public List<String> getAllPublishedOrganizations() {
        return this.currentSession().createNamedQuery("io.dockstore.webservice.core.Workflow.getPublishedOrganizations", String.class).list();
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
        String[] splitPath = Workflow.splitPath(path, false);

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
        Query<Workflow> query = namedTypedQuery(fullQueryName)
            .setParameter("sourcecontrol", converter.convertToEntityAttribute(sourcecontrol))
            .setParameter("organization", organization)
            .setParameter("repository", repository);

        return list(query);
    }

    /**
     * Finds the workflows matching the given workflow path. A service and
     * a BioWorkflow can have the same workflow path, which is why this returns
     * a List.
     * When findPublished is true, will only look at published workflows
     *
     * @param path
     * @param findPublished
     * @return Workflow matching the path
     */
    public List<Workflow> findByPath(String path, boolean findPublished) {
        String[] splitPath = Workflow.splitPath(path, true);

        // Not a valid path
        if (splitPath == null) {
            return Collections.emptyList();
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
        Query<Workflow> query = namedTypedQuery(fullQueryName)
            .setParameter("sourcecontrol", converter.convertToEntityAttribute(sourcecontrol))
            .setParameter("organization", organization)
            .setParameter("repository", repository);

        if (splitPath[entryNameIndex] != null) {
            query.setParameter("workflowname", workflowname);
        }

        return list(query);
    }

    /**
     * Finds a BioWorkflow, Apptool, or Service matching the given path.
     *
     * Initial implementation currently calls findByPath and filters the result; would ideally do it as a query with
     * no filtering instead.
     *
     * @param path
     * @param findPublished
     * @param clazz
     * @param <T>
     * @return
     */
    public <T extends Workflow> Optional<T> findByPath(String path, boolean findPublished, Class<T> clazz) {
        final List<Workflow> workflows = findByPath(path, findPublished);
        final List<T> filteredWorkflows  = workflows.stream()
                .filter(workflow -> workflow.getClass().equals(clazz))
                .map(clazz::cast)
                .toList();
        if (filteredWorkflows.size() > 1) {
            // DB constraints should never let this happen, I think
            throw new CustomWebApplicationException("Entries with the same path exist", HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
        return filteredWorkflows.size() == 1 ? Optional.of(filteredWorkflows.get(0)) : Optional.empty();
    }

    /**
     * Find if a path already exists in the BioWorkflow, Apptool, or Notebook tables, since we do not want duplicate names between them.
     * @param path
     * @return
     */
    public void checkForDuplicateAcrossTables(String path) {
        final Optional<Workflow> existing = findByPath(path, false).stream()
            .filter(workflow -> workflow.getClass() != Service.class)
            .findFirst();

        existing.ifPresent(e -> {
            String term = e.getEntryType().getTerm();
            throw new CustomWebApplicationException("A " + term + " with the same path already exists. Add the 'name' field to the entry you are currently trying to register to give it a unique path.", HttpStatus.SC_BAD_REQUEST);
        });
    }

    public List<Workflow> findByPaths(List<String> paths, boolean findPublished) {
        List<Predicate> predicates = new ArrayList<>();
        SourceControlConverter converter = new SourceControlConverter();

        // Create dynamic query using a CriteriaBuilder instance for all paths in the given list of strings
        CriteriaBuilder cb = currentSession().getCriteriaBuilder();
        CriteriaQuery<Workflow> q = cb.createQuery(Workflow.class);
        Root<Workflow> entry = q.from(Workflow.class);

        for (String path : paths) {
            String[] splitPath = Workflow.splitPath(path, true);

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

        return list(q);
    }

    public List<Workflow> findByPathWithoutUser(SourceControl sourceControl, String organization, String repository,
        final User user) {
        return list(namedTypedQuery("io.dockstore.webservice.core.Workflow.findByPathWithoutUser")
            .setParameter("sourcecontrol", sourceControl)
            .setParameter("organization", organization)
            .setParameter("repository", repository)
            .setParameter("user", user));

    }

    public List<Workflow> findPublishedBySourceOrgRepo(SourceControl sourceControl, String organization, String repository) {
        return list(namedTypedQuery("io.dockstore.webservice.core.Workflow.findPublishedBySourceOrgRepo")
                .setParameter("sourcecontrol", sourceControl)
                .setParameter("organization", organization)
                .setParameter("repository", repository));
    }

    public List<Workflow> findPublishedBySourceOrgRepoLatestReleaseDate(SourceControl sourceControl, String organization, String repository, Integer daysSinceLastRelease) {
        return list(namedTypedQuery("io.dockstore.webservice.core.Workflow.findPublishedBySourceOrgRepoReleaseDate")
                .setParameter("releaseDate", getTimestamp(daysSinceLastRelease))
                .setParameter("sourcecontrol", sourceControl)
                .setParameter("organization", organization)
                .setParameter("repository", repository));
    }

    private static Timestamp getTimestamp(Integer daysSinceLastRelease) {
        return Timestamp.valueOf(LocalDateTime.now().minusDays(daysSinceLastRelease));
    }

    public List<Workflow> findPublishedBySourceControl(SourceControl sourceControl) {
        return list(namedTypedQuery("io.dockstore.webservice.core.Workflow.findPublishedBySourceControl")
                .setParameter("sourcecontrol", sourceControl));
    }

    public List<Workflow> findPublishedBySourceControlLatestReleaseDate(SourceControl sourceControl, Integer daysSinceLastRelease) {
        return list(namedTypedQuery("io.dockstore.webservice.core.Workflow.findPublishedBySourceControlLatestReleaseDate")
                .setParameter("releaseDate", getTimestamp(daysSinceLastRelease))
                .setParameter("sourcecontrol", sourceControl));
    }

    public List<Workflow> findByGitUrl(String giturl) {
        return list(namedTypedQuery("io.dockstore.webservice.core.Workflow.findByGitUrl")
            .setParameter("gitUrl", giturl));
    }

    public List<Workflow> findPublishedByOrganization(String organization) {
        return list(namedTypedQuery("io.dockstore.webservice.core.Workflow.findPublishedByOrganization")
            .setParameter("organization", organization));
    }

    public List<Workflow> findByOrganization(SourceControl sourceControl, String organization) {
        return list(namedTypedQuery("io.dockstore.webservice.core.Workflow.findByOrganization")
                .setParameter("organization", organization)
                .setParameter("sourceControl", sourceControl));
    }

    public List<Workflow> findByOrganizationWithoutUser(SourceControl sourceControl, String organization, User user) {
        return list(namedTypedQuery("io.dockstore.webservice.core.Workflow.findByOrganizationWithoutUser")
                .setParameter("organization", organization)
                .setParameter("user", user)
                .setParameter("sourceControl", sourceControl));
    }

    public List<Workflow> findByOrganizationsWithoutUser(SourceControl sourceControl, List<String> organizations, User user) {
        return list(namedTypedQuery("io.dockstore.webservice.core.Workflow.findByOrganizationsWithoutUser")
            .setParameter("organizations", organizations.stream().map(String::toLowerCase).collect(Collectors.toList()))
            .setParameter("user", user)
            .setParameter("sourceControl", sourceControl));
    }

    public Workflow findByAlias(String alias) {
        return uniqueResult(namedTypedQuery("io.dockstore.webservice.core.Workflow.getByAlias").setParameter("alias", alias));
    }

    public List<Workflow> findAllWorkflows(int offset, int pageSize) {
        return namedTypedQuery(
            "io.dockstore.webservice.core.Workflow.findAllWorkflows")
            .setMaxResults(pageSize)
            .setFirstResult(offset)
            .list();
    }

    /**
     * Finds a workflow based on a workflow version id. If the workflow cannot be found
     * and an exception is generated an empty optional is returned
     *
     * @param workflowVersionId the id of the workflow version
     * @return optional workflow
     */
    public Optional<Workflow> getWorkflowByWorkflowVersionId(long workflowVersionId) {
        try {
            Workflow workflow = uniqueResult(namedTypedQuery("io.dockstore.webservice.core.Workflow.findWorkflowByWorkflowVersionId")
                    .setParameter("workflowVersionId", workflowVersionId));
            return Optional.of(workflow);
        } catch (NoResultException nre) {
            LOG.error("Could get workflow based on workflow version id " + workflowVersionId + ". Error is " + nre.getMessage(), nre);
            return Optional.empty();
        }
    }
}
