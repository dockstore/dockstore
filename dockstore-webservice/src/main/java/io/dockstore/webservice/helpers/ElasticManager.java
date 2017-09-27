package io.dockstore.webservice.helpers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.jdbi.ToolDAO;
import io.dockstore.webservice.jdbi.WorkflowDAO;
import io.dropwizard.jackson.Jackson;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpStatus;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.entity.NStringEntity;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author gluu
 * @since 26/07/17
 */
public class ElasticManager {
    public static DockstoreWebserviceConfiguration config;
    public static String hostname;
    public static int port;
    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticManager.class);
    private static ToolDAO toolDAO = null;
    private static WorkflowDAO workflowDAO = null;
    private List<Long> toolIds;
    private List<Long> workflowIds;

    public ElasticManager() {

    }

    public static DockstoreWebserviceConfiguration getConfig() {
        return config;
    }

    public static void setConfig(DockstoreWebserviceConfiguration config) {
        ElasticManager.config = config;
        ElasticManager.hostname = config.getEsConfiguration().getHostname();
        ElasticManager.port = config.getEsConfiguration().getPort();
    }

    public List<Long> getToolIds() {
        return toolIds;
    }

    public void setToolIds(ArrayList<Long> toolIds) {
        this.toolIds = toolIds;
    }

    public List<Long> getWorkflowIds() {
        return workflowIds;
    }

    public void setWorkflowIds(ArrayList<Long> workflowIds) {
        this.workflowIds = workflowIds;
    }

    private String getNDJSONFromIDs() {
        List<Entry> entries = new ArrayList<>();
        toolIds.forEach(id -> entries.add(toolDAO.findPublishedById(id)));
        workflowIds.forEach(id -> entries.add(workflowDAO.findPublishedById(id)));
        return getNDJSON(entries);
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
        doc.put("doc", entry);
        doc.put("doc_as_upsert", true);
        try {
            builder.append(mapper.writeValueAsString(doc));
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return builder.toString();
    }

    /**
     * This handles the index for elastic search
     *
     * @param entry   The entry to be converted into a document
     * @param command The command to perform for the document, either "update" or "delete" document
     */
    public void handleIndexUpdate(Entry entry, ElasticMode command) {
        LOGGER.info("Performing index update with " + command + ".");
        if (ElasticManager.hostname == null || ElasticManager.hostname.isEmpty()) {
            LOGGER.error("No elastic search host found.");
            return;
        }
        if (!checkValid(entry, command)) {
            LOGGER.error("Could not perform the elastic search index update.");
            return;
        }
        String json = getDocumentValueFromEntry(entry);
        try (RestClient restClient = RestClient.builder(new HttpHost(ElasticManager.hostname, ElasticManager.port, "http")).build()) {
            String entryType = entry instanceof Tool ? "tool" : "workflow";
            HttpEntity entity = new NStringEntity(json, ContentType.APPLICATION_JSON);
            org.elasticsearch.client.Response post;
            switch (command) {
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
                LOGGER.info("Could not submit index to elastic search. " + post.getStatusLine().getReasonPhrase());
            }
        } catch (IOException e) {
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
    private boolean checkValid(Entry entry, ElasticMode command) {
        boolean published = entry.getIsPublished();
        switch (command) {
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

    /**
     * Gets the json used to update labels
     *
     * @param entry The entry whose labels have been updated
     * @return The json used to update labels
     */
    public String updateDocumentValueFromLabels(Entry entry) {
        Gson gson = new GsonBuilder().create();
        StringBuilder builder = new StringBuilder();
        Map<String, Set> labelsMap = new HashMap<>();
        labelsMap.put("labels", entry.getLabels());
        Map<String, Object> doc = new HashMap<>();
        doc.put("doc", labelsMap);
        builder.append(gson.toJson(doc, Map.class));
        return builder.toString();
    }

    public void bulkUpsert(List<Entry> entries) {
        try (RestClient restClient = RestClient.builder(new HttpHost(ElasticManager.hostname, ElasticManager.port, "http")).build()) {
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
     * Gets the json used for bulk insert
     *
     * @param publishedEntries A list of published entries
     * @return The json used for bulk insert
     */
    public String getNDJSON(List<Entry> publishedEntries) {
        ObjectMapper mapper = Jackson.newObjectMapper();
        Gson gson = new GsonBuilder().create();
        StringBuilder builder = new StringBuilder();
        publishedEntries.forEach(entry -> {
            Map<String, Map<String, String>> index = new HashMap<>();
            Map<String, String> internal = new HashMap<>();
            internal.put("_id", String.valueOf(entry.getId()));
            internal.put("_type", entry instanceof Tool ? "tool" : "workflow");
            index.put("index", internal);
            builder.append(gson.toJson(index));
            builder.append('\n');
            try {
                builder.append(mapper.writeValueAsString(entry));
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
            builder.append('\n');
        });
        return builder.toString();
    }
}
