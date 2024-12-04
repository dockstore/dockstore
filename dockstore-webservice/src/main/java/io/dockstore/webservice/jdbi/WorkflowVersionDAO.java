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

import static io.dockstore.webservice.jdbi.EntryDAO.INVALID_SORTCOL_MESSAGE;

import com.google.common.base.Strings;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.WorkflowVersion;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.Attribute;
import java.util.ArrayList;
import java.util.List;
import org.apache.http.HttpStatus;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author dyuen
 */
public class WorkflowVersionDAO extends VersionDAO<WorkflowVersion> {

    private static final Logger LOG = LoggerFactory.getLogger(WorkflowVersionDAO.class);


    public WorkflowVersionDAO(SessionFactory sessionFactory) {
        super(sessionFactory);
    }

    public WorkflowVersion findByAlias(String alias) {
        return uniqueResult(namedTypedQuery("io.dockstore.webservice.core.WorkflowVersion.getByAlias").setParameter("alias", alias));
    }


    /**
     * Returns workflow versions by its workflow id. This is used for pagination.
     *
     * @param workflowId id of workflow
     * @param limit max limit of versions to return
     * @param offset the index of the first version in the list of versions to return
     * @param sortOrder either "asc" or "desc" to implement sorting
     * @param sortCol name of column to sort by
     * @param excludeHidden boolean value to exclude hidden versions (used for public page)
     *
     */
    public List<WorkflowVersion> getWorkflowVersionsByWorkflowId(long workflowId, int limit, int offset, String sortOrder, String sortCol, boolean excludeHidden) {
        CriteriaBuilder cb = currentSession().getCriteriaBuilder();
        CriteriaQuery<WorkflowVersion> query = criteriaQuery();
        Root<WorkflowVersion> version = query.from(WorkflowVersion.class);

        List<Predicate> predicates = processQuery(sortCol, sortOrder, cb, query, version);
        predicates.add(cb.equal(version.get("parent").get("id"), workflowId));
        if (excludeHidden) {
            predicates.add(cb.isFalse(version.get("versionMetadata").get("hidden")));
        }
        query.where(predicates.toArray(new Predicate[]{}));
        query.select(version);

        TypedQuery<WorkflowVersion> typedQuery = currentSession().createQuery(query).setFirstResult(offset).setMaxResults(limit);
        return typedQuery.getResultList();
    }

    public WorkflowVersion getWorkflowVersionByWorkflowIdAndVersionName(long workflowId, String name) {
        Query<WorkflowVersion> query = namedTypedQuery("io.dockstore.webservice.core.WorkflowVersion.getByWorkflowIdAndVersionName");
        query.setParameter("id", workflowId);
        query.setParameter("name", name);
        return uniqueResult(query);
    }

    public List<WorkflowVersion> getTagsByWorkflowIdOrderedByLastModified(long workflowId, int limit) {
        Query<WorkflowVersion> query = namedTypedQuery("io.dockstore.webservice.core.WorkflowVersion.getTagsByWorkflowIdOrderedByLastModified");
        query.setParameter("id", workflowId);
        query.setMaxResults(limit);
        return query.getResultList();
    }

    private List<Predicate> processQuery(String sortCol, String sortOrder, CriteriaBuilder cb, CriteriaQuery query, Root<WorkflowVersion> version) {
        List<Predicate> predicates = new ArrayList<>();

        Path<Object> versionId = version.get("id");
        if (!Strings.isNullOrEmpty(sortCol)) {
            Path<Object> sortPath;
            Path<Object> versionMetadata = version.get("versionMetadata");
            if ("open".equalsIgnoreCase(sortCol)) {
                sortPath = versionMetadata.get("publicAccessibleTestParameterFile");
            } else if ("descriptorTypeVersions".equalsIgnoreCase(sortCol)) {
                sortPath = versionMetadata.get("descriptorTypeVersions");
            } else {
                boolean hasSortCol = version.getModel()
                        .getAttributes()
                        .stream()
                        .map(Attribute::getName)
                        .anyMatch(sortCol::equalsIgnoreCase);

                if (!hasSortCol) {
                    LOG.error(INVALID_SORTCOL_MESSAGE);
                    throw new CustomWebApplicationException(INVALID_SORTCOL_MESSAGE,
                            HttpStatus.SC_BAD_REQUEST);

                }
                sortPath = version.get(sortCol);
            }
            if ("desc".equalsIgnoreCase(sortOrder)) {
                query.orderBy(cb.desc(sortPath), cb.desc(versionId));
            } else {
                query.orderBy(cb.asc(sortPath), cb.asc(versionId));
            }
        } else {
            if ("desc".equalsIgnoreCase(sortOrder)) {
                query.orderBy(cb.desc(versionId));
            } else {
                query.orderBy(cb.asc(versionId));
            }
        }
        return predicates;
    }
}
