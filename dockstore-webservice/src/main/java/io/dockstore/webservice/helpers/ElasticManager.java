package io.dockstore.webservice.helpers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.jdbi.ToolDAO;
import io.dockstore.webservice.jdbi.WorkflowDAO;
import io.dropwizard.jackson.Jackson;

/**
 * @author gluu
 * @since 26/07/17
 */
public class ElasticManager {
    private static ToolDAO toolDAO = null;
    private static WorkflowDAO workflowDAO = null;
    List<Long> toolIds;
    List<Long> workflowIds;

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
