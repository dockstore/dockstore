/*
 * Copyright 2021 OICR and UCSC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.dockstore.webservice.resources;

import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.CategorySummary;
import io.dockstore.webservice.core.Collection;
import io.dockstore.webservice.core.CollectionEntry;
import io.dockstore.webservice.core.Label;
import io.dockstore.webservice.jdbi.EntryDAO;
import io.dockstore.webservice.jdbi.VersionDAO;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.http.HttpStatus;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class CollectionHelper {

    private static final Logger LOG = LoggerFactory.getLogger(CollectionHelper.class);
    private final SessionFactory sessionFactory;
    private final EntryDAO<?> entryDAO;
    private final VersionDAO versionDAO;

    CollectionHelper(SessionFactory sessionFactory, EntryDAO<?> entryDAO, VersionDAO versionDAO) {
        this.sessionFactory = sessionFactory;
        this.entryDAO = entryDAO;
        this.versionDAO = versionDAO;
    }

    public void throwExceptionForNullCollection(Collection collection) {
        if (collection == null) {
            String msg = "Collection not found.";
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_NOT_FOUND);
        }
    }

    public void evictAndSummarize(Collection collection) {
        Session currentSession = sessionFactory.getCurrentSession();
        currentSession.evict(collection);
        // Ensure that entries is empty
        // This is probably unnecessary
        collection.setEntryVersions(new HashSet<>());
        collection.setWorkflowsLength(entryDAO.getBioWorkflowsLength(collection.getId()));
        collection.setToolsLength(entryDAO.getToolsLength(collection.getId()) +  entryDAO.getAppToolsLength(collection.getId()));
        collection.setNotebooksLength(entryDAO.getNotebooksLength(collection.getId()));
        collection.setServicesLength(entryDAO.getServicesLength(collection.getId()));
    }

    public void evictAndSummarize(java.util.Collection<? extends Collection> c) {
        c.forEach(this::evictAndSummarize);
    }

    public void evictAndAddEntries(Collection collection) {
        Session currentSession = sessionFactory.getCurrentSession();
        currentSession.evict(collection);
        List<CollectionEntry> collectionBioWorkflows = entryDAO.getCollectionBioWorkflows(collection.getId());
        List<CollectionEntry> collectionAppTools = entryDAO.getCollectionAppTools(collection.getId());
        List<CollectionEntry> collectionNotebooks = entryDAO.getCollectionNotebooks(collection.getId());
        List<CollectionEntry> collectionServices = entryDAO.getCollectionServices(collection.getId());
        List<CollectionEntry> collectionTools = entryDAO.getCollectionTools(collection.getId());
        List<CollectionEntry> collectionToolsWithVersions = entryDAO.getCollectionToolsWithVersions(collection.getId());
        List<CollectionEntry> collectionBioWorkflowsWithVersions = entryDAO.getCollectionBioWorkflowsWithVersions(collection.getId());
        List<CollectionEntry> collectionAppToolsWithVersions = entryDAO.getCollectionAppToolsWithVersions(collection.getId());
        List<CollectionEntry> collectionNotebooksWithVersions = entryDAO.getCollectionNotebooksWithVersions(collection.getId());
        List<CollectionEntry> collectionServicesWithVersions = entryDAO.getCollectionServicesWithVersions(collection.getId());
        List<CollectionEntry> collectionEntries = new ArrayList<>();
        collectionEntries.addAll(collectionBioWorkflows);
        collectionEntries.addAll(collectionBioWorkflowsWithVersions);
        collectionEntries.addAll(collectionAppTools);
        collectionEntries.addAll(collectionAppToolsWithVersions);
        collectionEntries.addAll(collectionNotebooks);
        collectionEntries.addAll(collectionNotebooksWithVersions);
        collectionEntries.addAll(collectionServices);
        collectionEntries.addAll(collectionServicesWithVersions);
        collectionEntries.addAll(collectionTools);
        collectionEntries.addAll(collectionToolsWithVersions);
        collectionEntries.forEach(entry -> {
            List<Label> labels = entryDAO.getLabelByEntryId(entry.getId());
            List<String> labelStrings = labels.stream().map(Label::getValue).collect(Collectors.toList());
            entry.setLabels(labelStrings);
            entry.setVerified(!versionDAO.findEntryVersionsWithVerifiedPlatforms(entry.getId()).isEmpty());
            List<CategorySummary> summaries = entryDAO.findCategorySummariesByEntryId(entry.getId());
            entry.setCategorySummaries(summaries);
            switch (entry.getEntryType()) {
            case "tool":
                entry.setDescriptorTypes(entryDAO.getToolsDescriptorTypes(entry.getId()));
                break;
            case "workflow":
                entry.setDescriptorTypes(entryDAO.getWorkflowsDescriptorTypes(entry.getId()));
                break;
            case "apptool":
                entry.setDescriptorTypes(entryDAO.getWorkflowsDescriptorTypes(entry.getId()));
                // we get file descriptor types like workflows, but make the UI treat these as tools (so icon and url work)
                entry.setEntryType("tool");
                break;
            case "notebook":
                entry.setDescriptorTypes(entryDAO.getWorkflowsDescriptorTypes(entry.getId()));
                entry.setEntryType("notebook");
                break;
            case "service":
                entry.setDescriptorTypes(entryDAO.getWorkflowsDescriptorTypes(entry.getId()));
                entry.setEntryType("service");
                break;
            default:
                throw new UnsupportedOperationException("unexpected entry type when constructing collection");
            }
        });
        collection.setCollectionEntries(collectionEntries);
        collection.setWorkflowsLength(collectionBioWorkflows.size() + (long)collectionBioWorkflowsWithVersions.size());
        collection.setToolsLength(collectionTools.size() + (long)collectionToolsWithVersions.size() + collectionAppTools.size() + collectionAppToolsWithVersions.size());
        collection.setNotebooksLength(collectionNotebooks.size() + (long)collectionNotebooksWithVersions.size());
        collection.setServicesLength(collectionServices.size() + (long)collectionServicesWithVersions.size());
    }
}
