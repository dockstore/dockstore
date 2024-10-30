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

    public List<WorkflowVersion> getWorkflowVersionsByWorkflowId(long workflowId, int limit, int offset, String sortOrder, String sortCol) {
        CriteriaBuilder cb = currentSession().getCriteriaBuilder();
        CriteriaQuery<WorkflowVersion> query = criteriaQuery();
        Root<WorkflowVersion> version = query.from(WorkflowVersion.class);

        List<Predicate> predicates = processQuery(sortCol, sortOrder, cb, query, version);
        predicates.add(cb.equal(version.get("parent").get("id"), workflowId));
        query.where(predicates.toArray(new Predicate[]{}));
        query.select(version);

        TypedQuery<WorkflowVersion> typedQuery = currentSession().createQuery(query).setFirstResult(offset).setMaxResults(limit);
        return typedQuery.getResultList();
    }

    public List<WorkflowVersion> getPublicWorkflowVersionsByWorkflowId(long workflowId, int limit, int offset, String sortOrder, String sortCol) {
        CriteriaBuilder cb = currentSession().getCriteriaBuilder();
        CriteriaQuery<WorkflowVersion> query = criteriaQuery();
        Root<WorkflowVersion> version = query.from(WorkflowVersion.class);

        List<Predicate> predicates = processQuery(sortCol, sortOrder, cb, query, version);
        predicates.add(cb.equal(version.get("parent").get("id"), workflowId));
        predicates.add(cb.isFalse(version.get("versionMetadata").get("hidden")));
        query.where(predicates.toArray(new Predicate[]{}));

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

        if (!Strings.isNullOrEmpty(sortCol)) {
            if ("open".equalsIgnoreCase(sortCol)) {
                if ("desc".equalsIgnoreCase(sortOrder)) {
                    query.orderBy(cb.desc(version.get("versionMetadata").get("publicAccessibleTestParameterFile")), cb.desc(version.get("id")));
                } else {
                    query.orderBy(cb.asc(version.get("versionMetadata").get("publicAccessibleTestParameterFile")), cb.asc(version.get("id")));
                }
            } else if ("descriptorTypeVersions".equals(sortCol)) {
                if ("desc".equalsIgnoreCase(sortOrder)) {
                    query.orderBy(cb.desc(version.get("versionMetadata").get("descriptorTypeVersions")), cb.desc(version.get("id")));
                } else {
                    query.orderBy(cb.asc(version.get("versionMetadata").get("descriptorTypeVersions")), cb.asc(version.get("id")));
                }
            } else {
                boolean hasSortCol = version.getModel()
                        .getAttributes()
                        .stream()
                        .map(Attribute::getName)
                        .anyMatch(sortCol::equals);

                if (!hasSortCol) {
                    LOG.error(INVALID_SORTCOL_MESSAGE);
                    throw new CustomWebApplicationException(INVALID_SORTCOL_MESSAGE,
                            HttpStatus.SC_BAD_REQUEST);

                } else {
                    Path<Object> sortPath = version.get(sortCol);
                    if ("asc".equalsIgnoreCase(sortOrder)) {
                        query.orderBy(cb.asc(sortPath), cb.asc(version.get("id")));
                    } else {
                        query.orderBy(cb.desc(sortPath), cb.desc(version.get("id")));
                    }
                }
            }
        }
        return predicates;
    }
}
