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
import io.dockstore.webservice.core.Version.ReferenceType;
import io.dockstore.webservice.core.WorkflowVersion;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.Attribute;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
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
    public List<WorkflowVersion> getWorkflowVersionsByWorkflowId(long workflowId, int limit, int offset, String sortOrder, String sortCol, boolean excludeHidden, long representativeVersionId) {
        CriteriaBuilder cb = currentSession().getCriteriaBuilder();
        CriteriaQuery<WorkflowVersion> query = criteriaQuery();
        Root<WorkflowVersion> version = query.from(WorkflowVersion.class);

        List<Predicate> predicates = processQuery(sortCol, sortOrder, cb, query, version, representativeVersionId);
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

    @SuppressWarnings("checkstyle:MagicNumber")
    private List<Predicate> processQuery(String sortCol, String sortOrder, CriteriaBuilder cb, CriteriaQuery query, Root<WorkflowVersion> version, long representativeVersionId) {
        List<Predicate> predicates = new ArrayList<>();

        Path<?> versionId = version.get("id");
        Path<Timestamp> lastModified = version.get("lastModified");

        Expression<?> sortExpression;
        if (!Strings.isNullOrEmpty(sortCol)) {
            Path<?> versionMetadata = version.get("versionMetadata");
            if ("open".equalsIgnoreCase(sortCol)) {
                sortExpression = versionMetadata.get("publicAccessibleTestParameterFile");
            } else if ("descriptorTypeVersions".equalsIgnoreCase(sortCol)) {
                sortExpression = versionMetadata.get("descriptorTypeVersions");
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
                sortExpression = version.get(sortCol);
            }
        } else {
            // Create a sort expression that "scores" each version.
            // To ensure that "relevant" versions appear first, we rank "mainstem" branches, tags, more recently-modified versions, valid versions, and versions with metrics higher.
            Path<?> name = version.get("name");
            Path<?> referenceType = version.get("referenceType");
            Path<Boolean> valid = version.get("valid");
            Path<? extends Collection> metricsByPlatform = version.get("metricsByPlatform");

            // Compute the "age" of the version, which is the date since it was last modified, in days.
            // To prevent the sort order from constantly changing, calculate the age relative to the beginning of tomorrow.
            Expression<Double> secondsPerDay = cb.literal(24 * 3600.);
            Expression<Timestamp> now = cb.currentTimestamp();
            Expression<Timestamp> modified = cb.coalesce(lastModified, version.get("dbCreateDate"));
            Expression<Double> nowDays = cb.toDouble(cb.quot(cb.function("date_part", Double.class, cb.literal("epoch"), now), secondsPerDay));
            Expression<Double> modifiedDays = cb.toDouble(cb.quot(cb.function("date_part", Double.class, cb.literal("epoch"), modified), secondsPerDay));
            Expression<Double> tomorrowDays = cb.sum(cb.function("floor", Double.class, nowDays), 1.);
            Expression<Double> ageDays = cb.diff(tomorrowDays, modifiedDays);

            // Calculate some weights based on the "age", validity, and presence of metrics.
            Expression<Double> ageWeight = cb.toDouble(cb.quot(1., cb.function("greatest", Double.class, ageDays, cb.literal(0.5))));
            Expression<Double> validWeight = cb.<Double>selectCase()
                .when(cb.isTrue(valid), 3.)
                .otherwise(1.);
            Expression<Double> metricsWeight = cb.<Double>selectCase()
                .when(cb.isNotEmpty(metricsByPlatform), 2.)
                .otherwise(1.);

            // Calculate a base "score", which ranks "mainstem" branches highest and tags higher.
            Expression<Double> baseScore = cb.<Double>selectCase()
                .when(cb.equal(name, "master"), 20.)
                .when(cb.equal(name, "main"), 20.)
                .when(cb.equal(name, "develop"), 19.)
                .when(cb.equal(referenceType, ReferenceType.TAG), 2.)
                .otherwise(1.);

            // Combine the weights with our base score to get a weighted score.
            Expression<Double> weightedScore = cb.prod(cb.prod(cb.prod(baseScore, ageWeight), validWeight), metricsWeight);

            // The final "sort expression" uses our weighted score, except for the representative version, which always ranks highest.
            sortExpression = cb.selectCase()
                .when(cb.equal(versionId, representativeVersionId), 1e9)
                .otherwise(weightedScore);
        }

        if ("asc".equalsIgnoreCase(sortOrder)) {
            query.orderBy(cb.asc(sortExpression), cb.asc(lastModified), cb.asc(versionId));
        } else {
            query.orderBy(cb.desc(sortExpression), cb.desc(lastModified), cb.desc(versionId));
        }

        return predicates;
    }
}
