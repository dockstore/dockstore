/*
 *    Copyright 2019 OICR
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
package io.dockstore.webservice.helpers.statelisteners;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.BioWorkflow;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.Label;
import io.dockstore.webservice.core.Service;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.helpers.StateManagerMode;
import io.dropwizard.jackson.Jackson;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpStatus;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.entity.NStringEntity;
import org.elasticsearch.client.RestClient;
import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Formerly the ElasticManager, this listens for changes that might affect elastic search
 */
public class ElasticListener implements StateListenerInterface {
    public static DockstoreWebserviceConfiguration config;
    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticListener.class);
    private static final ObjectMapper MAPPER = Jackson.newObjectMapper();
    private static final String MAPPER_ERROR = "Could not convert Dockstore entry to Elasticsearch object";
    private String hostname;
    private int port;

    @Override
    public void setConfig(DockstoreWebserviceConfiguration config) {
        hostname = config.getEsConfiguration().getHostname();
        port = config.getEsConfiguration().getPort();
    }

    /**
     * Manually eager load certain fields
     * @param entry
     */
    private void eagerLoadEntry(Entry entry) {
        Hibernate.initialize(entry.getAliases());
    }

    @Override
    public void handleIndexUpdate(Entry entry, StateManagerMode command) {
        eagerLoadEntry(entry);
        entry = filterCheckerWorkflows(entry);
        // #2771 will need to disable this and properly create objects to get services into the index
        entry = entry instanceof Service ? null : entry;
        if (entry == null) {
            return;
        }
        LOGGER.info("Performing index update with " + command + ".");
        if (hostname == null || hostname.isEmpty()) {
            LOGGER.error("No elastic search host found.");
            return;
        }
        if (!checkValid(entry, command)) {
            LOGGER.info("Could not perform the elastic search index update.");
            return;
        }
        String json;
        json = getDocumentValueFromEntry(entry);
        try (RestClient restClient = RestClient.builder(new HttpHost(hostname, port, "http")).build()) {
            String entryType = entry instanceof Tool ? "tool" : "workflow";
            HttpEntity entity = new NStringEntity(json, ContentType.APPLICATION_JSON);
            org.elasticsearch.client.Response post;
            switch (command) {
            case PUBLISH:
            case UPDATE:
                post = restClient
                    .performRequest("POST", "/entry/" + entryType + "/" + entry.getId() + "/_update", Collections.emptyMap(), entity);
                break;
            case DELETE:
                post = restClient.performRequest("DELETE", "/entry/" + entryType + "/" + entry.getId(), Collections.emptyMap(), entity);
                break;
            default:
                throw new RuntimeException("Unknown index command: " + command);
            }
            int statusCode = post.getStatusLine().getStatusCode();
            if (statusCode == HttpStatus.SC_OK || statusCode == HttpStatus.SC_CREATED) {
                LOGGER.info("Successful " + command + ".");
            } else {
                LOGGER.error("Could not submit index to elastic search. " + post.getStatusLine().getReasonPhrase());
            }
        } catch (Exception e) {
            LOGGER.error("Could not submit index to elastic search. " + e.getMessage());
        }
    }

    /**
     * Check if the entry is valid to perform the elastic operation
     *
     * @param entry   The entry to check
     * @param command The command that will be used
     * @return Whether or not the entry is valid
     */
    private boolean checkValid(Entry entry, StateManagerMode command) {
        boolean published = entry.getIsPublished();
        switch (command) {
        case PUBLISH:
        case UPDATE:
            if (published) {
                return true;
            }
            break;
        case DELETE:
            // Try deleting no matter what
            return true;
        default:
            LOGGER.error("Unrecognized Elasticsearch command.");
            return false;
        }
        return false;
    }

    @Override
    public void bulkUpsert(List<Entry> entries) {
        entries.forEach(this::eagerLoadEntry);
        entries = filterCheckerWorkflows(entries);
        // #2771 will need to disable this and properly create objects to get services into the index
        entries = entries.stream().filter(entry -> !(entry instanceof Service)).collect(Collectors.toList());
        if (entries.isEmpty()) {
            return;
        }
        try (RestClient restClient = RestClient.builder(new HttpHost(hostname, port, "http")).build()) {
            String newlineDJSON = getNDJSON(entries);
            HttpEntity bulkEntity = new NStringEntity(newlineDJSON, ContentType.APPLICATION_JSON);
            org.elasticsearch.client.Response post = restClient.performRequest("POST", "/entry/_bulk", Collections.emptyMap(), bulkEntity);
            if (post.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                throw new CustomWebApplicationException("Could not submit index to elastic search", HttpStatus.SC_INTERNAL_SERVER_ERROR);
            }
        } catch (IOException e) {
            LOGGER.error("Could not submit index to elastic search. " + e.getMessage());
        }
    }

    /**
     * This converts the entry into a document for elastic search to use
     *
     * @param entry The entry that needs updating
     * @return The entry converted into a json string
     */
    private String getDocumentValueFromEntry(Entry entry) {
        ObjectMapper mapper = Jackson.newObjectMapper();
        StringBuilder builder = new StringBuilder();
        Map<String, Object> doc = new HashMap<>();
        try {
            JsonNode jsonNode = dockstoreEntryToElasticSearchObject(entry);
            doc.put("doc", jsonNode);
            doc.put("doc_as_upsert", true);
            builder.append(mapper.writeValueAsString(doc));
        } catch (IOException e) {
            throw new CustomWebApplicationException(MAPPER_ERROR,
                HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
        return builder.toString();
    }

    /**
     * Gets the json used for bulk insert
     *
     * @param publishedEntries A list of published entries
     * @return The json used for bulk insert
     */
    private String getNDJSON(List<Entry> publishedEntries) {
        Gson gson = new GsonBuilder().create();
        StringBuilder builder = new StringBuilder();
        publishedEntries.forEach(entry -> {
            entry.getWorkflowVersions().forEach(entryVersion -> {
                ((Version)entryVersion).updateVerified();
            });
            Map<String, Map<String, String>> index = new HashMap<>();
            Map<String, String> internal = new HashMap<>();
            internal.put("_id", String.valueOf(entry.getId()));
            internal.put("_type", entry instanceof Tool ? "tool" : "workflow");
            index.put("index", internal);
            builder.append(gson.toJson(index));
            builder.append('\n');
            try {
                builder.append(MAPPER.writeValueAsString(dockstoreEntryToElasticSearchObject(entry)));
            } catch (IOException e) {
                throw new CustomWebApplicationException(MAPPER_ERROR, HttpStatus.SC_INTERNAL_SERVER_ERROR);
            }
            builder.append('\n');
        });
        return builder.toString();
    }

    /**
     * This should be using an actual Elasticsearch object class instead of jsonNode
     *
     * @param entry The Dockstore entry
     * @return The Elasticsearch object string to be placed into the index
     * @throws IOException  Mapper problems
     */
    public static JsonNode dockstoreEntryToElasticSearchObject(final Entry entry) throws IOException {
        Set<Version> workflowVersions = entry.getWorkflowVersions();
        boolean verified = workflowVersions.stream().anyMatch(Version::isVerified);
        Set<String> verifiedPlatforms = getVerifiedPlatforms(workflowVersions);
        Entry detachedEntry = removeIrrelevantProperties(entry);
        JsonNode jsonNode = MAPPER.readTree(MAPPER.writeValueAsString(detachedEntry));
        ((ObjectNode)jsonNode).put("verified", verified);
        ((ObjectNode)jsonNode).put("verified_platforms", MAPPER.valueToTree(verifiedPlatforms));
        return jsonNode;
    }

    /**
     * Remove some stuff that should not be indexed by ES.
     * This is not ideal, we should be including things we want indexed, not removing.
     * @param entry
     */
    private static Entry removeIrrelevantProperties(final Entry entry) {
        Entry detachedEntry;
        if (entry instanceof Tool) {
            Tool tool = (Tool) entry;
            Tool detachedTool = new Tool();
            tool.getWorkflowVersions().forEach(version -> {
                Hibernate.initialize(version.getSourceFiles());
            });

            // These are for facets
            detachedTool.setDefaultWdlPath(tool.getDefaultWdlPath());
            detachedTool.setDefaultCwlPath(tool.getDefaultCwlPath());
            detachedTool.setNamespace(tool.getNamespace());
            detachedTool.setRegistry(tool.getRegistry());
            detachedTool.setPrivateAccess(tool.isPrivateAccess());

            // These are for table
            detachedTool.setGitUrl(tool.getGitUrl());
            detachedTool.setName(tool.getName());
            detachedTool.setToolname(tool.getToolname());
            detachedEntry = detachedTool;
        } else if (entry instanceof BioWorkflow) {
            BioWorkflow bioWorkflow = (BioWorkflow) entry;
            BioWorkflow detachedBioWorkflow = new BioWorkflow();
            // These are for facets
            detachedBioWorkflow.setDescriptorType(bioWorkflow.getDescriptorType());
            detachedBioWorkflow.setSourceControl(bioWorkflow.getSourceControl());
            detachedBioWorkflow.setOrganization(bioWorkflow.getOrganization());

            // These are for table
            detachedBioWorkflow.setWorkflowName(bioWorkflow.getWorkflowName());
            detachedBioWorkflow.setRepository(bioWorkflow.getRepository());
            detachedBioWorkflow.setGitUrl(bioWorkflow.getGitUrl());
            detachedEntry = detachedBioWorkflow;
        } else {
            return entry;
        }
        detachedEntry.setDescription(entry.getDescription());
        detachedEntry.setAuthor(entry.getAuthor());
        detachedEntry.setAliases(entry.getAliases());
        detachedEntry.setLabels((SortedSet<Label>)entry.getLabels());
        detachedEntry.setCheckerWorkflow(entry.getCheckerWorkflow());
        Set<Version> detachedVersions = cloneWorkflowVersion(entry.getWorkflowVersions());
        detachedEntry.setWorkflowVersions(detachedVersions);
        entry.getStarredUsers().forEach(user -> detachedEntry.addStarredUser((User)user));
        String defaultVersion = entry.getDefaultVersion();
        if (defaultVersion != null) {
            boolean saneDefaultVersion = detachedVersions.stream().anyMatch(version -> defaultVersion.equals(version.getName()) || defaultVersion.equals(version.getReference()));
            if (saneDefaultVersion) {
                // If the tool/workflow has a default version, only keep the default version (and its sourcefile contents and description)
                Set<Version> newWorkflowVersions = detachedEntry.getWorkflowVersions();
                newWorkflowVersions.forEach(version -> {
                    if (!defaultVersion.equals(version.getReference()) && !defaultVersion.equals(version.getName())) {
                        version.setDescriptionAndDescriptionSource(null, null);
                        SortedSet<SourceFile> sourceFiles = version.getSourceFiles();
                        sourceFiles.forEach(sourceFile -> sourceFile.setContent(""));
                    }
                });
            } else {
                LOGGER.error("Entry has a default version that doesn't exist: " + entry.getEntryPath());
            }
        }
        return detachedEntry;
    }

    private static Set<Version> cloneWorkflowVersion(final Set<Version> originalWorkflowVersions) {
        Set<Version> detatchedVersions = new HashSet<>();
        originalWorkflowVersions.forEach(workflowVersion -> {
            Version detatchedVersion = workflowVersion.createEmptyVersion();
            detatchedVersion.setDescriptionAndDescriptionSource(workflowVersion.getDescription(), workflowVersion.getDescriptionSource());
            detatchedVersion.setInputFileFormats(new TreeSet<>(workflowVersion.getInputFileFormats()));
            detatchedVersion.setOutputFileFormats(new TreeSet<>(workflowVersion.getOutputFileFormats()));
            detatchedVersion.setName(workflowVersion.getName());
            detatchedVersion.setReference(workflowVersion.getReference());
            SortedSet<SourceFile> sourceFiles = workflowVersion.getSourceFiles();
            sourceFiles.forEach(sourceFile -> {
                Gson gson = new Gson();
                String gsonString = gson.toJson(sourceFile);
                SourceFile detachedSourceFile = gson.fromJson(gsonString, SourceFile.class);
                detatchedVersion.addSourceFile(detachedSourceFile);
            });
            detatchedVersion.updateVerified();
            detatchedVersions.add(detatchedVersion);
        });
        return detatchedVersions;
    }


    private static Set<String> getVerifiedPlatforms(Set<? extends Version> workflowVersions) {
        Set<String> platforms = new TreeSet<>();
        workflowVersions.forEach(workflowVersion -> {
            SortedSet<SourceFile> sourceFiles = workflowVersion.getSourceFiles();
            sourceFiles.forEach(sourceFile -> {
                Map<String, SourceFile.VerificationInformation> verifiedBySource = sourceFile.getVerifiedBySource();
                platforms.addAll(verifiedBySource.keySet());
            });
        });
        return platforms;
    }

    /**
     * If entry is a checker workflow, return null.  Otherwise, return entry
     * @param entry     The entry to check
     * @return          null if checker, entry otherwise
     */
    private static Entry filterCheckerWorkflows(Entry entry) {
        return entry instanceof Workflow && ((Workflow)entry).isIsChecker() ? null : entry;
    }

    /**
     * Remove checker workflow from list of entries
     * @param entries   List of all entries
     * @return          List of entries without checker workflows
     */
    public static List<Entry> filterCheckerWorkflows(List<Entry> entries) {
        return entries.stream().filter(entry -> entry instanceof Tool || (entry instanceof Workflow && !((Workflow)entry).isIsChecker())).collect(Collectors.toList());
    }
}
