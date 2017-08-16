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
    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticManager.class);
    public static String hostname;
    public static int port;
    public static DockstoreWebserviceConfiguration config;
    private static ToolDAO toolDAO = null;
    private static WorkflowDAO workflowDAO = null;
    List<Long> toolIds;
    List<Long> workflowIds;

    public ElasticManager() {
        this.hostname = config.getEsConfiguration().getHostname();
        this.port = config.getEsConfiguration().getPort();
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
        toolIds.forEach(id -> {
            entries.add(toolDAO.findPublishedById(id));
        });
        workflowIds.forEach(id -> {
            entries.add(workflowDAO.findPublishedById(id));
        });
        return getNDJSON(entries);
    }

    public String getDocumentValueFromEntry(Entry entry) {
        ObjectMapper mapper = Jackson.newObjectMapper();
        StringBuilder builder = new StringBuilder();
        Map<String, Object> doc = new HashMap<>();
        doc.put("doc", entry);
        try {
            builder.append(mapper.writeValueAsString(doc));
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return builder.toString();
    }

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

    public void updateDocument(Entry entry) {
        if (entry.getIsPublished()) {
            String json = getDocumentValueFromEntry(entry);
            LOGGER.error(json);
            if (!this.hostname.isEmpty()) {
                try (RestClient restClient = RestClient.builder(new HttpHost(this.hostname, this.port, "http")).build()) {
                    String entryType = entry instanceof Tool ? "tool" : "workflow";
                    HttpEntity entity = new NStringEntity(json, ContentType.APPLICATION_JSON);
                    org.elasticsearch.client.Response post = restClient
                            .performRequest("POST", "/entry/" + entryType + "/" + entry.getId() + "/_update", Collections.emptyMap(), entity);
                    if (post.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                        throw new CustomWebApplicationException("Could not submit index to elastic search", HttpStatus.SC_INTERNAL_SERVER_ERROR);
                    }
                } catch (IOException e) {
                    throw new CustomWebApplicationException(e.getMessage(), HttpStatus.SC_INTERNAL_SERVER_ERROR);
                }
            }
        }
    }

    public String getNDJSON(List<Entry> published) {
        ObjectMapper mapper = Jackson.newObjectMapper();
        Gson gson = new GsonBuilder().create();
        StringBuilder builder = new StringBuilder();
        published.forEach(entry -> {
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
