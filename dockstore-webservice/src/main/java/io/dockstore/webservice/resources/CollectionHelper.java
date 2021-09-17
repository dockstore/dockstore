package io.dockstore.webservice.resources;

import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.Collection;
import io.dockstore.webservice.core.CollectionEntry;
import io.dockstore.webservice.core.Label;
import io.dockstore.webservice.jdbi.EntryDAO;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.http.HttpStatus;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;

class CollectionHelper {

    private final Logger log;
    private final SessionFactory sessionFactory;
    private final EntryDAO entryDAO;

    CollectionHelper(Logger log, SessionFactory sessionFactory, EntryDAO entryDAO) {
        this.log = log;
        this.sessionFactory = sessionFactory;
        this.entryDAO = entryDAO;
    }

    public void throwExceptionForNullCollection(Collection collection) {
        if (collection == null) {
            String msg = "Collection not found.";
            log.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_NOT_FOUND);
        }
    }

    public void evictAndSummarize(Collection collection) {
        Session currentSession = sessionFactory.getCurrentSession();
        currentSession.evict(collection);
        // Ensure that entries is empty
        // This is probably unnecessary
        collection.setEntries(new HashSet<>());
        collection.setWorkflowsLength(entryDAO.getWorkflowsLength(collection.getId()));
        collection.setToolsLength(entryDAO.getToolsLength(collection.getId()));
    }

    public void evictAndSummarize(java.util.Collection<? extends Collection> c) {
        c.forEach(collection -> evictAndSummarize(collection));
    }

    public void evictAndAddEntries(Collection collection) {
        Session currentSession = sessionFactory.getCurrentSession();
        currentSession.evict(collection);
        List<CollectionEntry> collectionWorkflows = entryDAO.getCollectionWorkflows(collection.getId());
        List<CollectionEntry> collectionServices = entryDAO.getCollectionServices(collection.getId());
        List<CollectionEntry> collectionTools = entryDAO.getCollectionTools(collection.getId());
        List<CollectionEntry> collectionWorkflowsWithVersions = entryDAO.getCollectionWorkflowsWithVersions(collection.getId());
        List<CollectionEntry> collectionServicesWithVersions = entryDAO.getCollectionServicesWithVersions(collection.getId());
        List<CollectionEntry> collectionToolsWithVersions = entryDAO.getCollectionToolsWithVersions(collection.getId());
        List<CollectionEntry> collectionEntries = new ArrayList<>();
        collectionEntries.addAll(collectionWorkflows);
        collectionEntries.addAll(collectionServices);
        collectionEntries.addAll(collectionTools);
        collectionEntries.addAll(collectionWorkflowsWithVersions);
        collectionEntries.addAll(collectionServicesWithVersions);
        collectionEntries.addAll(collectionToolsWithVersions);
        collectionEntries.forEach(entry -> {
            List<Label> labels = entryDAO.getLabelByEntryId(entry.getId());
            List<String> labelStrings = labels.stream().map(Label::getValue).collect(Collectors.toList());
            entry.setLabels(labelStrings);
            List<String> names = entryDAO.findCategoryNamesByEntryId(entry.getId());
            entry.setCategoryNames(names);
            if (entry.getEntryType().equals("tool")) {
                entry.setDescriptorTypes(entryDAO.getToolsDescriptorTypes(entry.getId()));
            } else if (entry.getEntryType().equals("workflow")) {
                entry.setDescriptorTypes(entryDAO.getWorkflowsDescriptorTypes(entry.getId()));
            }
        });
        collection.setCollectionEntries(collectionEntries);
        collection.setWorkflowsLength(collectionWorkflows.size() + collectionWorkflowsWithVersions.size());
        collection.setToolsLength(collectionTools.size() + collectionToolsWithVersions.size());
    }

    public void evictAndAddEntries(java.util.Collection<? extends Collection> c) {
        c.forEach(collection -> evictAndAddEntries(collection));
    }
}
