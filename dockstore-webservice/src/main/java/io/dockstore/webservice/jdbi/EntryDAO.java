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

import static io.dockstore.webservice.core.Entry.ENTRY_GET_EXECUTION_METRIC_PARTNERS;
import static io.dockstore.webservice.core.Entry.ENTRY_GET_VALIDATION_METRIC_PARTNERS;

import com.google.common.base.Strings;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.Partner;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.Author;
import io.dockstore.webservice.core.Category;
import io.dockstore.webservice.core.CategorySummary;
import io.dockstore.webservice.core.CollectionEntry;
import io.dockstore.webservice.core.CollectionOrganization;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.Entry.EntryLiteAndVersionName;
import io.dockstore.webservice.core.Label;
import io.dockstore.webservice.core.SourceControlConverter;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.database.EntryLite;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import jakarta.persistence.metamodel.Attribute;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.http.HttpStatus;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.hibernate.query.criteria.JpaCriteriaQuery;
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
    protected static final String ENTRY_IDS = "entryIds";

    private Class<T> typeOfT;

    public static final String INVALID_SORTCOL_MESSAGE = "Could not process query due to the invalid sortCol value.";

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
        final int minEntryNamePathLength = 4; // <registry>/<org>/<repo>/<entry-name>
        final int pathLength = path.split("/").length;
        // Determine which type of path to look for first: path with an entry name or path without an entry name
        boolean hasEntryName = pathLength >= minEntryNamePathLength;

        MutablePair<String, Entry> results = findEntryByPath(path, hasEntryName, isPublished);

        if (pathLength >= minEntryNamePathLength && results == null) {
            // If <repo> contains slashes, there are two scenarios that can form the same entry path. In the following scenarios, assume that <registry> and <org> are the same.
            // Scenario 1: <repo> = 'foo', <entry-name> = 'bar'
            // Scenario 2: <repo> = 'foo/bar', <entry-name> = NULL
            // Need to try the opposite scenario if we couldn't find the entry using the initial scenario (i.e. if we first tried to find a path with an entry name, try to find one without).
            results = findEntryByPath(path, !hasEntryName, isPublished);
        }

        return results;
    }

    public MutablePair<String, Entry> findEntryByPath(String path, boolean hasEntryName, boolean isPublished) {
        String queryString = "Entry.";
        if (isPublished) {
            queryString += "getPublishedEntryByPath";
        } else {
            queryString += "getEntryByPath";
        }

        // split path
        String[] splitPath = Tool.splitPath(path, hasEntryName);

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
            Long id = (Long)(pair.get(0))[1];
            Long longId = id;
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
        session.remove(entry);
        session.flush();
    }

    public Entry<? extends Entry, ? extends Version> getGenericEntryById(long id) {
        return this.currentSession().createNamedQuery("Entry.getGenericEntryById", Entry.class).setParameter("id", id).uniqueResult();
    }

    public Entry<? extends Entry, ? extends Version>  getGenericEntryByAlias(String alias) {
        return this.currentSession().createNamedQuery("Entry.getGenericEntryByAlias", Entry.class).setParameter("alias", alias).uniqueResult();
    }

    public List<CollectionOrganization> findCollectionsByEntryId(long entryId) {
        return this.currentSession().createNamedQuery("io.dockstore.webservice.core.Entry.findCollectionsByEntryId", CollectionOrganization.class).setParameter("entryId", entryId).list();
    }

    public List<CategorySummary> findCategorySummariesByEntryId(long entryId) {
        return this.currentSession().createNamedQuery("io.dockstore.webservice.core.Entry.findCategorySummariesByEntryId", CategorySummary.class).setParameter("entryId", entryId).list();
    }

    public List<Category> findCategoriesByEntryId(long entryId) {
        return this.currentSession().createNamedQuery("io.dockstore.webservice.core.Entry.findCategoriesByEntryId", Category.class).setParameter("entryId", entryId).list();
    }

    /**
     * Retrieve the list of categories containing each of the specified Entries.
     * @param entryIds a list of Entry IDs
     * @return a map of each Entry contained by one-or-more Categories to a list of all Categories that contain it, any Entry contained by zero Categories is not included in the map
     */
    public Map<Entry, List<Category>> findCategoriesByEntryIds(List<Long> entryIds) {
        // run a query to determine the categories that contain the specified entries, where the result is a list of unique entry/category pairs.
        // for example, if Entry E is in categories C and D, the result would be [[E, C], [E, D]].

        List<Object[]> results = list(this.currentSession().getNamedQuery("io.dockstore.webservice.core.Entry.findEntryCategoryPairsByEntryIds").setParameterList(
                ENTRY_IDS, entryIds));

        // convert the list of entry/category pairs to a map (as described in the javadoc above).
        Map<Entry, List<Category>> entryToCategories = new HashMap<>();
        results.forEach(result -> {
            Entry entry = (Entry)result[0];
            Category category = (Category)result[1];
            entryToCategories.computeIfAbsent(entry, k -> new ArrayList<>()).add(category);
        });

        return (entryToCategories);
    }

    public T findPublishedById(long id) {
        return (T) this.currentSession().createNamedQuery("io.dockstore.webservice.core." + typeOfT.getSimpleName() + ".findPublishedById", Entry.class).setParameter("id", id).uniqueResult();
    }

    public List<EntryLite> findEntryVersions(long userId) {
        return this.currentSession().createNamedQuery("io.dockstore.webservice.core." + typeOfT.getSimpleName() + ".getEntryLiteByUserId", EntryLite.class).setParameter("userId", userId).list();
    }
    public List<T> findMyEntries(long userId) {
        return (List<T>) this.currentSession().createNamedQuery("io.dockstore.webservice.core." + typeOfT.getSimpleName() + ".getEntriesByUserId", Entry.class).setParameter("userId", userId).list();
    }
    public List<T> findMyEntriesPublished(long userId) {
        return (List<T>) this.currentSession().createNamedQuery("io.dockstore.webservice.core." + typeOfT.getSimpleName() + ".getPublishedEntriesByUserId", Entry.class).setParameter("userId", userId).list();
    }

    public List<EntryLiteAndVersionName> findEntryVersionsToAggregate() {
        return this.currentSession().createNamedQuery("io.dockstore.webservice.core." + typeOfT.getSimpleName() + ".getEntryLiteVersionsToAggregate", EntryLiteAndVersionName.class).list();
    }

    /**
     * Retrieve a list of all workflow entries contained in a given collection. Note a workflow in this case are EntryTypes that are inherited from the Workflow class.
     * This includes AppTools, BioWorkflows, Notebooks, and Services.
     * @param collectionId collection ID
     * @return a list of all workflow entries in the given collection.
     */
    public List<CollectionEntry> getAllCollectionWorkflows(long collectionId) {
        return this.currentSession().createNamedQuery("Entry.getAllCollectionWorkflows", CollectionEntry.class).setParameter("collectionId", collectionId).list();
    }
    public List<CollectionEntry> getCollectionBioWorkflows(long collectionId) {
        return this.currentSession().createNamedQuery("Entry.getCollectionBioWorkflows", CollectionEntry.class).setParameter("collectionId", collectionId).list();
    }
    public List<CollectionEntry> getCollectionAppTools(long collectionId) {
        return this.currentSession().createNamedQuery("Entry.getCollectionAppTools", CollectionEntry.class).setParameter("collectionId", collectionId).list();
    }
    public List<CollectionEntry> getCollectionNotebooks(long collectionId) {
        return this.currentSession().createNamedQuery("Entry.getCollectionNotebooks", CollectionEntry.class).setParameter("collectionId", collectionId).list();
    }

    public long getBioWorkflowsLength(long collectionId) {
        return this.currentSession().createNamedQuery("Entry.getBioWorkflowsLength", Long.class).setParameter("collectionId", collectionId).getSingleResult();
    }
    public long getAppToolsLength(long collectionId) {
        return this.currentSession().createNamedQuery("Entry.getAppToolsLength", Long.class).setParameter("collectionId", collectionId).getSingleResult();
    }
    public long getNotebooksLength(long collectionId) {
        return this.currentSession().createNamedQuery("Entry.getNotebooksLength", Long.class).setParameter("collectionId", collectionId).getSingleResult();
    }

    public long getServicesLength(long collectionId) {
        return this.currentSession().createNamedQuery("Entry.getServicesLength", Long.class).setParameter("collectionId", collectionId).getSingleResult();
    }

    public List<CollectionEntry> getCollectionServices(long collectionId) {
        return this.currentSession().createNamedQuery("Entry.getCollectionServices", CollectionEntry.class).setParameter("collectionId", collectionId).list();
    }

    public List<CollectionEntry> getCollectionTools(long collectionId) {
        return this.currentSession().createNamedQuery("Entry.getCollectionTools", CollectionEntry.class).setParameter("collectionId", collectionId).list();
    }

    public long getToolsLength(long collectionId) {
        return this.currentSession().createNamedQuery("Entry.getToolsLength", Long.class).setParameter("collectionId", collectionId).getSingleResult();
    }

    /**
     * Retrieve a list of all workflow entries that contains versions in a given collection. Note a workflow in this case are EntryTypes that are inherited from the Workflow class.
     * This includes AppTools, BioWorkflows, Notebooks, and Services.
     * @param collectionId collection ID
     * @return a list of all Workflow entries with versions in the given collection.
     */
    public List<CollectionEntry> getAllCollectionWorkflowsWithVersions(long collectionId) {
        return this.currentSession().createNamedQuery("Entry.getAllCollectionWorkflowsWithVersions", CollectionEntry.class).setParameter("collectionId", collectionId).list();
    }
    public List<CollectionEntry> getCollectionBioWorkflowsWithVersions(long collectionId) {
        return this.currentSession().createNamedQuery("Entry.getCollectionBioWorkflowsWithVersions", CollectionEntry.class).setParameter("collectionId", collectionId).list();
    }

    public List<CollectionEntry> getCollectionAppToolsWithVersions(long collectionId) {
        return this.currentSession().createNamedQuery("Entry.getCollectionAppToolsWithVersions", CollectionEntry.class).setParameter("collectionId", collectionId).list();
    }

    public List<CollectionEntry> getCollectionNotebooksWithVersions(long collectionId) {
        return this.currentSession().createNamedQuery("Entry.getCollectionNotebooksWithVersions", CollectionEntry.class).setParameter("collectionId", collectionId).list();
    }

    public List<CollectionEntry> getCollectionServicesWithVersions(long collectionId) {
        return this.currentSession().createNamedQuery("Entry.getCollectionServicesWithVersions", CollectionEntry.class).setParameter("collectionId", collectionId).list();
    }

    public List<CollectionEntry> getCollectionToolsWithVersions(long collectionId) {
        return this.currentSession().createNamedQuery("Entry.getCollectionToolsWithVersions", CollectionEntry.class).setParameter("collectionId", collectionId).list();
    }

    public List<T> findAllPublished(Integer offset, Integer limit, String filter, String sortCol, String sortOrder) {
        return findAllPublished(offset, limit, filter, sortCol, sortOrder, typeOfT);
    }

    /**
     *
     * @param offset
     * @param limit
     * @param filter
     * @param sortCol the column to sort on, note that if the column to sort by has null values, they will be omitted
     * @param sortOrder default sort order is ascending
     * @param classType
     * @return
     */
    public List<T> findAllPublished(Integer offset, Integer limit, String filter, String sortCol, String sortOrder, Class<T> classType) {
        HibernateCriteriaBuilder cb = currentSession().getCriteriaBuilder();
        CriteriaQuery<T> query = criteriaQuery();
        Root<T> entry = query.from(classType != null ? classType : typeOfT);
        processQuery(filter, sortCol, sortOrder, cb, query, entry);
        query.select(entry);

        TypedQuery<T> typedQuery = currentSession().createQuery(query);
        if (offset != null) {
            typedQuery.setFirstResult(Math.max(offset, 0));
        }
        if (limit != null) {
            typedQuery.setMaxResults(Math.max(limit, 1));
        }
        return typedQuery.getResultList();
    }

    public long countAllHosted(long userid) {
        return ((Long)namedQuery("Entry.hostedWorkflowCount").setParameter("userid", userid).getSingleResult());
    }

    // TODO: these methods should be merged with the proprietary version in EntryDAO, but should be a major version refactoring.
    @SuppressWarnings("checkstyle:ParameterNumber")
    public long countAllPublished(DescriptorLanguage descriptorLanguage, String registry, String organization, String name, String toolname, String description, String author, Boolean checker) {
        final HibernateCriteriaBuilder cb = currentSession().getCriteriaBuilder();
        final JpaCriteriaQuery<Long> q = cb.createQuery(Long.class);

        Root<T> entryRoot = generatePredicate(descriptorLanguage, registry, organization, name, toolname, description, author, checker, cb, q);

        q.select(cb.count(entryRoot));
        return currentSession().createQuery(q).getSingleResult();
    }

    public long countAllPublished(Optional<String> filter) {
        return countAllPublished(filter, null);
    }


    public long countAllPublished(Optional<String> filter, Class<T> classType) {
        if (filter.isEmpty()) {
            return countAllPublished();
        }
        HibernateCriteriaBuilder cb = currentSession().getCriteriaBuilder();
        JpaCriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<T> entry = query.from(classType != null ? classType : typeOfT);
        processQuery(filter.get(), "", "", cb, query, entry);
        query.select(cb.count(entry));
        return currentSession().createQuery(query).getSingleResult();
    }

    private long countAllPublished() {
        return this.currentSession().createNamedQuery("io.dockstore.webservice.core." + typeOfT.getSimpleName() + ".countAllPublished", Long.class).getSingleResult();
    }

    public List<Label> getLabelByEntryId(long entryId) {
        return this.currentSession().createNamedQuery("io.dockstore.webservice.core.Entry.findLabelByEntryId", Label.class).setParameter("entryId", entryId).list();
    }

    public List<String> getToolsDescriptorTypes(long entryId) {
        return (List<String>)this.currentSession().getNamedQuery("Entry.findToolsDescriptorTypes").setParameter("entryId", entryId)
                .getSingleResult();
    }

    public List<String> getWorkflowsDescriptorTypes(long entryId) {
        return Arrays.asList(this.currentSession().getNamedQuery("Entry.findWorkflowsDescriptorTypes").setParameter("entryId", entryId).getSingleResult().toString());
    }

    public Map<Long, List<Partner>> findExecutionPartners(List<Long> entryIds) {

        final List<Entry.EntryIdAndPartner> list = (List<Entry.EntryIdAndPartner>)namedQuery(ENTRY_GET_EXECUTION_METRIC_PARTNERS).setParameterList(ENTRY_IDS,
                entryIds).list();
        return partnerMetricsToMap(list);
    }

    public Map<Long, List<Partner>> findValidationPartners(List<Long> entryIds) {
        final List<Entry.EntryIdAndPartner> list = (List<Entry.EntryIdAndPartner>)namedQuery(ENTRY_GET_VALIDATION_METRIC_PARTNERS).setParameterList(ENTRY_IDS, entryIds).list();
        return partnerMetricsToMap(list);
    }

    private Map<Long, List<Partner>> partnerMetricsToMap(List<Entry.EntryIdAndPartner> entryIdAndPartnerMetrics) {
        final Map<Long, List<Partner>> map = new HashMap<>();
        entryIdAndPartnerMetrics.forEach(partnerMetric -> map.computeIfAbsent(partnerMetric.entryId(), v -> new ArrayList<>()).add(partnerMetric.partner()));
        return map;
    }

    public List<Entry> getPublishedEntriesWithNoTopics(int offset, int limit) {
        return this.currentSession().createNamedQuery(Entry.GET_PUBLISHED_ENTRIES_WITH_NO_TOPICS, Entry.class)
                .setFirstResult(offset)
                .setMaxResults(limit)
                .list();
    }

    public long countPublishedEntriesWithNoTopics() {
        return this.currentSession().createNamedQuery(Entry.COUNT_PUBLISHED_ENTRIES_WITH_NO_TOPICS, Long.class).getSingleResult();
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
                    cb.and(cb.isNotNull(entry.get(repoName)), cb.like(cb.upper(entry.get(repoName)), "%" + filter.toUpperCase() + "%")), //
                    addAuthorClauseToCriteriaBuilder(cb, entry, query.subquery(Author.class), filter), //
                    // TODO orcid authors are interesting since we load them dynamically and thus cannot query them from the database
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
                boolean hasSortCol = entry.getModel()
                        .getAttributes()
                        .stream()
                        .map(Attribute::getName)
                        .anyMatch(sortCol::equals);

                if (!hasSortCol) {
                    LOG.error(INVALID_SORTCOL_MESSAGE);
                    throw new CustomWebApplicationException(INVALID_SORTCOL_MESSAGE,
                            HttpStatus.SC_BAD_REQUEST);

                } else {
                    Path<Object> sortPath = entry.get(sortCol);
                    if (!Strings.isNullOrEmpty(sortOrder) && "desc".equalsIgnoreCase(sortOrder)) {
                        query.orderBy(cb.desc(sortPath), cb.desc(entry.get("id")));
                    } else {
                        query.orderBy(cb.asc(sortPath), cb.desc(entry.get("id")));
                    }
                    predicates.add(sortPath.isNotNull());
                }
            }
        }
        query.where(predicates.toArray(new Predicate[]{}));
    }

    protected Predicate andLike(CriteriaBuilder cb, Predicate existingPredicate, Path<String> column, Optional<String> value) {
        return value.map(val -> cb.and(existingPredicate, cb.like(column, wildcardLike(val))))
            .orElse(existingPredicate);
    }

    private String wildcardLike(String value) {
        return '%' + value + '%';
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    public List<T> filterTrsToolsGet(DescriptorLanguage descriptorLanguage, String registry, String organization, String name, String toolname,
        String description, String author, Boolean checker, int startIndex, int pageRemaining) {

        final HibernateCriteriaBuilder cb = currentSession().getCriteriaBuilder();
        final JpaCriteriaQuery<T> q = cb.createQuery(typeOfT);
        final Root<T> tRoot = generatePredicate(descriptorLanguage, registry, organization, name, toolname, description, author, checker, cb, q);
        // order by id
        q.orderBy(cb.asc(tRoot.get("id")));
        TypedQuery<T> query = currentSession().createQuery(q);
        query.setFirstResult(startIndex);
        query.setMaxResults(pageRemaining);
        return query.getResultList();
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    protected abstract Root<T> generatePredicate(DescriptorLanguage descriptorLanguage, String registry, String organization, String name, String toolname, String description, String author, Boolean checker,
        CriteriaBuilder cb, CriteriaQuery<?> q);

    @SuppressWarnings("checkstyle:ParameterNumber")
    protected Predicate getWorkflowPredicate(DescriptorLanguage descriptorLanguage, String registry, String organization, String name, String toolname, String description, String author,
        CriteriaBuilder cb, SourceControlConverter converter, Root<?> entryRoot, CriteriaQuery<?> query) {
        Predicate predicate = cb.isTrue(entryRoot.get("isPublished"));
        predicate = andLike(cb, predicate, entryRoot.get("organization"), Optional.ofNullable(organization));
        predicate = andLike(cb, predicate, entryRoot.get("repository"), Optional.ofNullable(name));
        predicate = andLike(cb, predicate, entryRoot.get("workflowName"), Optional.ofNullable(toolname));
        predicate = andLike(cb, predicate, entryRoot.get("description"), Optional.ofNullable(description));

        if (author != null) {
            predicate = addAuthorClauseToCriteriaBuilder(cb, entryRoot, query.subquery(Author.class), author);
        }

        if (descriptorLanguage != null) {
            predicate = cb.and(predicate, cb.equal(entryRoot.get("descriptorType"), descriptorLanguage));
        }
        if (registry != null) {
            predicate = cb.and(predicate, cb.equal(entryRoot.get("sourceControl"), converter.convertToEntityAttribute(registry)));
        }
        return predicate;
    }

    static Predicate addAuthorClauseToCriteriaBuilder(CriteriaBuilder cb, Root<?> entryRoot, Subquery<Author> subQuery, String author) {
        Root<Author> authorRoot = subQuery.from(Author.class);
        final Predicate notEmpty = cb.exists(
                subQuery.select(authorRoot).where(cb.and(cb.and(cb.equal(authorRoot.get("versionid"), entryRoot.get("actualDefaultVersion")))), (cb.like(cb.upper(authorRoot.get("name")), "%" + author.toUpperCase() + "%"))));
        return cb.and(cb.isNotNull(entryRoot.get("actualDefaultVersion")), notEmpty);
    }
}
