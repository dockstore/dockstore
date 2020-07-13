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

import java.lang.reflect.ParameterizedType;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import io.dockstore.webservice.core.BioWorkflow;
import io.dockstore.webservice.core.BioWorkflow_;
import io.dockstore.webservice.core.CollectionEntry;
import io.dockstore.webservice.core.CollectionOrganization;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.database.EntryLite;
import io.dockstore.webservice.core.dto.TrsImageDTO;
import io.dockstore.webservice.core.dto.TrsToolDTO;
import io.dockstore.webservice.core.dto.TrsToolVersion;
import io.dockstore.webservice.core.dto.TrsToolVersionDescriptorType;
import io.dockstore.webservice.core.dto.WorkflowTrsToolDTO;
import org.apache.commons.lang3.tuple.MutablePair;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author dyuen
 */
public abstract class EntryDAO<T extends Entry> extends AbstractDockstoreDAO<T> {

    private static final Logger LOG = LoggerFactory.getLogger(EntryDAO.class);

    final int registryIndex = 0;
    final int orgIndex = 1;
    final int repoIndex = 2;
    final int entryNameIndex = 3;

    private Class<T> typeOfT;

    EntryDAO(SessionFactory factory) {
        super(factory);
        /*
          ewwww, don't try this at home from https://stackoverflow.com/questions/4837190/java-generics-get-class
         */
        this.typeOfT = (Class<T>)((ParameterizedType)getClass().getGenericSuperclass()).getActualTypeArguments()[0];
    }

    public T findById(Long id) {
        return get(id);
    }

    public MutablePair<String, Entry> findEntryByPath(String path, boolean isPublished) {
        String queryString = "Entry.";
        if (isPublished) {
            queryString += "getPublishedEntryByPath";
        } else {
            queryString += "getEntryByPath";
        }

        // split path
        String[] splitPath = Tool.splitPath(path);

        // Not a valid path
        if (splitPath == null) {
            return null;
        }

        // Valid path
        String one = splitPath[registryIndex];
        String two = splitPath[orgIndex];
        String three = splitPath[repoIndex];
        String four = splitPath[entryNameIndex];

        if (four == null) {
            queryString += "NullName";
        }

        Query query = super.namedQuery(queryString);

        query.setParameter("one", one);
        query.setParameter("two", two);
        query.setParameter("three", three);

        if (four != null) {
            query.setParameter("four", four);
        }

        List<Object[]> pair = list(query);
        MutablePair<String, Entry> results = null;
        if (pair.size() > 0) {
            String type = (String)(pair.get(0))[0];
            BigInteger id = (BigInteger)(pair.get(0))[1];
            Long longId = id.longValue();
            if ("workflow".equals(type)) {
                results = new MutablePair<>("workflow", this.currentSession().get(Workflow.class, Objects.requireNonNull(longId)));
            } else {
                results = new MutablePair<>("tool", this.currentSession().get(Tool.class, Objects.requireNonNull(longId)));
            }
        }
        return results;
    }

    public long create(T entry) {
        return persist(entry).getId();
    }

    public void delete(T entry) {
        Session session = currentSession();
        session.delete(entry);
        session.flush();
    }

    public Entry<? extends Entry, ? extends Version> getGenericEntryById(long id) {
        return uniqueResult(this.currentSession().getNamedQuery("Entry.getGenericEntryById").setParameter("id", id));
    }

    public Entry<? extends Entry, ? extends Version> getGenericEntryByAlias(String alias) {
        return uniqueResult(this.currentSession().getNamedQuery("Entry.getGenericEntryByAlias").setParameter("alias", alias));
    }

    public List<CollectionOrganization> findCollectionsByEntryId(long entryId) {
        return list(this.currentSession().getNamedQuery("io.dockstore.webservice.core.Entry.findCollectionsByEntryId").setParameter("entryId", entryId));
    }

    public T findPublishedById(long id) {
        return (T)uniqueResult(
                this.currentSession().getNamedQuery("io.dockstore.webservice.core." + typeOfT.getSimpleName() + ".findPublishedById").setParameter("id", id));
    }

    public List<EntryLite> findEntryVersions(long userId) {
        return list(this.currentSession().getNamedQuery("io.dockstore.webservice.core." + typeOfT.getSimpleName() + ".getEntryLiteByUserId").setParameter("userId", userId));
    }
    public List<T> findMyEntries(long userId) {
        return list(this.currentSession().getNamedQuery("io.dockstore.webservice.core." + typeOfT.getSimpleName() + ".getEntriesByUserId").setParameter("userId", userId));
    }
    public List<T> findMyEntriesPublished(long userId) {
        return list(this.currentSession().getNamedQuery("io.dockstore.webservice.core." + typeOfT.getSimpleName() + ".getPublishedEntriesByUserId").setParameter("userId", userId));
    }

    public List<CollectionEntry> getCollectionWorkflows(long collectionId) {
        return list(this.currentSession().getNamedQuery("Entry.getCollectionWorkflows").setParameter("collectionId", collectionId));
    }

    public List<CollectionEntry> getCollectionServices(long collectionId) {
        return list(this.currentSession().getNamedQuery("Entry.getCollectionServices").setParameter("collectionId", collectionId));
    }

    public List<CollectionEntry> getCollectionTools(long collectionId) {
        return list(this.currentSession().getNamedQuery("Entry.getCollectionTools").setParameter("collectionId", collectionId));
    }
    public List<T> findAllPublished(String offset, Integer limit, String filter, String sortCol, String sortOrder) {
        return findAllPublished(offset, limit, filter, sortCol, sortOrder, typeOfT);
    }

    public List<T> findAllPublished(String offset, Integer limit, String filter, String sortCol, String sortOrder, Class<T> classType) {
        CriteriaBuilder cb = currentSession().getCriteriaBuilder();
        CriteriaQuery<T> query = criteriaQuery();
        Root<T> entry = query.from(classType != null ? classType : typeOfT);
        processQuery(filter, sortCol, sortOrder, cb, query, entry);
        query.select(entry);

        int primitiveOffset = Integer.parseInt(MoreObjects.firstNonNull(offset, "0"));
        TypedQuery<T> typedQuery = currentSession().createQuery(query).setFirstResult(primitiveOffset).setMaxResults(limit);
        return typedQuery.getResultList();
    }

    public List<T> findAllPublished() {
        return list(this.currentSession().getNamedQuery("io.dockstore.webservice.core." + typeOfT.getSimpleName() + ".findAllPublished"));
    }

    public long countAllHosted(long userid) {
        return ((BigInteger)namedQuery("Entry.hostedWorkflowCount").setParameter("userid", userid).getSingleResult()).longValueExact();
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    public List<TrsToolDTO> findAllTrsPublished(final Optional<String> registry, final Optional<String> organization,
            final Optional<Boolean> checker, final Optional<String> toolname, final Optional<String> author,
            final Optional<String> description, final Optional<String> offset, final int limit) {

        final List<TrsToolDTO> trsToolDTOS = getTrsTools(registry, organization, checker, toolname, author, description, limit);

        final Map<Long, List<TrsToolVersion>> versionMap = fetchTrsToolVersions(trsToolDTOS.stream().map(t -> t.getId()).collect(Collectors.toList()));

        trsToolDTOS.forEach(tool -> tool.getVersions().addAll(versionMap.getOrDefault(tool.getId(), Collections.emptyList())));

        return trsToolDTOS;
    }

    private Map<Long, List<TrsToolVersion>> fetchTrsToolVersions(final List<Long> workflowIds) {
        final List<TrsToolVersion> versions = list(namedQuery("io.dockstore.webservice.core.Entry.getNonHiddenVersions")
                .setParameterList("ids", workflowIds));

        final List<Long> versionIds = versions.stream().map(v -> v.getId()).collect(Collectors.toList());
        final List<TrsToolVersionDescriptorType> descriptorTypes = list(namedQuery("io.dockstore.webservice.core.Entry.findDescriptorTypes")
                .setParameterList("ids", versionIds));
        final Map<Long, List<TrsToolVersionDescriptorType>> versionDescriptorMap = descriptorTypes.stream()
                .collect(Collectors.groupingBy(TrsToolVersionDescriptorType::getVersionId));

        final List<TrsImageDTO> images = list(namedQuery("io.dockstore.webservice.core.Entry.getImagesForVersions").setParameterList("ids", versionIds));
        final Map<Long, List<TrsImageDTO>> versionImageMap = images.stream().collect(Collectors.groupingBy(TrsImageDTO::getVersionId));

        versions.forEach(version -> {
            final List<TrsToolVersionDescriptorType> descriptors = versionDescriptorMap.get(version.getId());
            if (descriptors != null) {
                version.getDescriptorTypes().addAll(descriptors.stream().map(d -> d.getFileType()).collect(Collectors.toList()));
            }
            final List<TrsImageDTO> trsImageDTOs = versionImageMap.get(version.getId());
            if (trsImageDTOs != null) {
                version.getImages().addAll(trsImageDTOs);
            }
        });

        return versions.stream().collect(Collectors.groupingBy(TrsToolVersion::getEntryId));
    }

    private List<TrsToolDTO> getTrsTools(final Optional<String> registry, final Optional<String> organization, final Optional<Boolean> checker,
            final Optional<String> toolname, final Optional<String> author, final Optional<String> description, final int limit) {
        final CriteriaBuilder cb = currentSession().getCriteriaBuilder();
        final CriteriaQuery<TrsToolDTO> query = cb.createQuery(TrsToolDTO.class);
        final Root<BioWorkflow> root = query.from(BioWorkflow.class);
        final Join<BioWorkflow, BioWorkflow> join = root.join(BioWorkflow_.checkerWorkflow, JoinType.LEFT);
        query.select(cb.construct(WorkflowTrsToolDTO.class, root.get(BioWorkflow_.id),
                root.get(BioWorkflow_.organization),
                root.get(BioWorkflow_.description),
                root.get(BioWorkflow_.sourceControl),
                root.get(BioWorkflow_.descriptorType),
                root.get(BioWorkflow_.repository),
                root.get(BioWorkflow_.workflowName),
                join.get(BioWorkflow_.sourceControl),
                join.get(BioWorkflow_.organization),
                join.get(BioWorkflow_.repository),
                join.get(BioWorkflow_.workflowName),
                root.get(BioWorkflow_.lastUpdated)));
        Predicate predicate = cb.isTrue(root.get(BioWorkflow_.isPublished));
        predicate = andLike(cb, predicate, root.get(BioWorkflow_.organization), organization);
        predicate = andLike(cb, predicate, root.get(BioWorkflow_.workflowName), toolname);
        predicate = andLike(cb, predicate, root.get(BioWorkflow_.author), author);
        predicate = andLike(cb, predicate, root.get(BioWorkflow_.description), description);
        if (checker.isPresent()) {
            predicate = cb.and(predicate, cb.isTrue(root.get(BioWorkflow_.isChecker)));
        }
        query.where(predicate);
        final Query<TrsToolDTO> toolQuery = currentSession().createQuery(query).setMaxResults(registry.isPresent() ? Integer.MAX_VALUE : limit);
        final List<TrsToolDTO> tools = toolQuery.getResultList();
        return filterByRegistry(registry, tools, limit);
    }


    /**
     * Filter is on SourceControl().toString(), which returns a value defined
     * in Java, different from what is stored in the DB. So yeah, don't think we
     * can do this in SQL. Fortunately, these objects should now be pretty light, and
     * I assume this filter is rarely used, so typically may not be a major impact.
     * @param registry
     * @param tools
     * @param limit
     * @return
     */
    private List<TrsToolDTO> filterByRegistry(final Optional<String> registry, final List<TrsToolDTO> tools, final int limit) {
        return registry.map(s -> tools.stream()
                .filter(tool -> tool.getSourceControl().toString().contains(s))
                .limit(limit)
                .collect(Collectors.toList()))
                .orElse(tools);
    }

    private Predicate andLike(CriteriaBuilder cb, Predicate existingPredicate, Path<String> column, Optional<String> value) {
        return value.map(val -> cb.and(existingPredicate, cb.like(column, wildcardLike(val))))
                .orElse(existingPredicate);
    }

    private String wildcardLike(String value) {
        return '%' + value + '%';
    }


    public long countAllPublished(Optional<String> filter) {
        if (filter.isEmpty()) {
            return countAllPublished();
        }
        CriteriaBuilder cb = currentSession().getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<T> entry = query.from(typeOfT);
        processQuery(filter.get(), "", "", cb, query, entry);
        query.select(cb.count(entry));
        return currentSession().createQuery(query).getSingleResult();
    }

    private long countAllPublished() {
        return (long)this.currentSession().getNamedQuery("io.dockstore.webservice.core." + typeOfT.getSimpleName() + ".countAllPublished").getSingleResult();
    }

    private void processQuery(String filter, String sortCol, String sortOrder, CriteriaBuilder cb, CriteriaQuery query, Root<T> entry) {
        List<Predicate> predicates = new ArrayList<>();
        if (!Strings.isNullOrEmpty(filter)) {
            // TODO: handle all search attributes that we want to hook up, this sucks since we didn't handle polymorphism quite right
            boolean toolMode = typeOfT == Tool.class;
            String nameName = toolMode ? "toolname" : "workflowName";
            String repoName = toolMode ? "name" : "repository";
            String orgName = toolMode ? "namespace" : "organization";

            predicates.add(cb.and(// get published workflows
                cb.isTrue(entry.get("isPublished")),
                // ensure we deal with null values and then do like queries on those non-null values
                cb.or(cb.and(cb.isNotNull(entry.get(nameName)), cb.like(cb.upper(entry.get(nameName)), "%" + filter.toUpperCase() + "%")), //
                    cb.and(cb.isNotNull(entry.get("author")), cb.like(cb.upper(entry.get("author")), "%" + filter.toUpperCase() + "%")), //
                    cb.and(cb.isNotNull(entry.get(repoName)), cb.like(cb.upper(entry.get(repoName)), "%" + filter.toUpperCase() + "%")), //
                    cb.and(cb.isNotNull(entry.get(orgName)), cb.like(cb.upper(entry.get(orgName)), "%" + filter.toUpperCase() + "%")))));

        } else {
            predicates.add(cb.isTrue(entry.get("isPublished")));
        }
        if (!Strings.isNullOrEmpty(sortCol)) {
            // sorting by stars is a special case since it needs a join
            if ("stars".equalsIgnoreCase(sortCol)) {
                if ("desc".equalsIgnoreCase(sortOrder)) {
                    query.orderBy(cb.desc(cb.size(entry.<Collection>get("starredUsers"))), cb.desc(entry.get("id")));
                } else {
                    query.orderBy(cb.asc(cb.size(entry.<Collection>get("starredUsers"))), cb.desc(entry.get("id")));
                }
            } else {
                Path<Object> sortPath = entry.get(sortCol);
                if (!Strings.isNullOrEmpty(sortOrder) && "desc".equalsIgnoreCase(sortOrder)) {
                    query.orderBy(cb.desc(sortPath), cb.desc(entry.get("id")));
                    predicates.add(sortPath.isNotNull());
                } else {
                    query.orderBy(cb.asc(sortPath), cb.desc(entry.get("id")));
                    predicates.add(sortPath.isNotNull());
                }
            }
        }
        query.where(predicates.toArray(new Predicate[]{}));
    }
}
