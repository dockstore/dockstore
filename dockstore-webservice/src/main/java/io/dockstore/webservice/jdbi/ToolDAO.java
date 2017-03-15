/*
 *    Copyright 2016 OICR
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

import com.github.jsonldjava.core.JsonLdError;
import com.github.jsonldjava.core.JsonLdOptions;
import com.github.jsonldjava.core.JsonLdProcessor;
import com.github.jsonldjava.utils.JsonUtils;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.ToolMode;
import org.hibernate.SessionFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.dockstore.webservice.DockstoreWebserviceApplication.yaml2json;

/**
 * @author xliu
 */
public class ToolDAO extends EntryDAO<Tool> {
    public ToolDAO(SessionFactory factory) {
        super(factory);
    }

    public List<Tool> findByPath(String path) {
        return list(namedQuery("io.dockstore.webservice.core.Tool.findByPath").setParameter("path", path));
    }

    public Tool findByToolPath(String path, String tool) {
        return uniqueResult(
                namedQuery("io.dockstore.webservice.core.Tool.findByToolPath").setParameter("path", path).setParameter("toolname", tool));
    }

    public List<Tool> findByMode(final ToolMode mode) {
        return list(namedQuery("io.dockstore.webservice.core.Tool.findByMode").setParameter("mode", mode));
    }

    public List<Tool> findPublishedByPath(String path) {
        return list(namedQuery("io.dockstore.webservice.core.Tool.findPublishedByPath").setParameter("path", path));
    }

    public Tool findPublishedByToolPath(String path, String tool) {
        return uniqueResult(namedQuery("io.dockstore.webservice.core.Tool.findPublishedByToolPath").setParameter("path", path)
                .setParameter("toolname", tool));
    }

    /**
     * Return a map with correct types for json-ld parsing
     * @param schemaMap map of json data
     * @param schemaVariable string which refers to http://schema.org/
     * @return map of json data which correctly adds @type annotations
     */
    private static Map<String, Object> addType(final Map<String, Object> schemaMap, final String schemaVariable) {
        Map<String, Object> merged = new HashMap<>(schemaMap);

        for (Map.Entry<String, Object> entry : schemaMap.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            Object copyValue = merged.get(key);

            if (value instanceof List) {
                for (int i = 0; i < ((ArrayList) value).size(); i++) {
                    Object node = ((ArrayList) value).get(i);

                    if (node instanceof Map) {
                        ((ArrayList) copyValue).set(i, addType((Map<String, Object>) node, schemaVariable));
                    }
                }
            } else if (value instanceof Map) {
                merged.replace(key, value, addType((Map) value, schemaVariable));
            } else if (value instanceof String) {
                String type = ((String) value).replace(schemaVariable + ":", "");

                if ("class".equals(key)) {
                    merged.put("@type", type);
                    merged.remove(key);
                }

            }
        }

        return merged;
    }

    /**
     * Return a map ready for integration with front-end json-ld script
     * @param schemaMap map of json-ld data
     * @param schemaVariable string which refers to http://schema.org/
     * @return map of json-ld data with variable name stripped
     */
    private static Map<String, Object> stripNamespace(final Map<String, Object> schemaMap, final String schemaVariable) {
        Map<String, Object> merged = new HashMap<>(schemaMap);

        for (Map.Entry<String, Object> entry : schemaMap.entrySet()) {

            String key = entry.getKey();
            Object value = entry.getValue();
            Object copyValue = merged.get(key);

            if (key.startsWith(schemaVariable)) {
                String revisedKey = key.replace(schemaVariable + ":", "");

                merged.put(revisedKey, value);
                merged.remove(key);
                key = revisedKey;
            }

            if (value instanceof List) {
                for (int i = 0; i < ((ArrayList) value).size(); i++) {
                    Object node = ((ArrayList) value).get(i);

                    if (node instanceof Map) {
                        ((ArrayList) copyValue).set(i, stripNamespace((Map<String, Object>) node, schemaVariable));
                    }
                }
            } else if (value instanceof Map) {
                merged.replace(key, value, stripNamespace((Map) value, schemaVariable));
            }
        }

        return merged;
    }

    /**
     * Return map of json-ld info retrieved from json
     * @param cwljson json converted from cwl document
     * @param schemaVariable string which refers to http://schema.org/
     * @return map of json-ld info
     */
    private static Map<String, Object> cwlJson2Map(final String cwljson, final String schemaVariable) {
        try {
            Map<String, Object> jsonObject = (Map<String, Object>) JsonUtils.fromString(cwljson);

            jsonObject = addType(jsonObject, schemaVariable);

            // compact json
            Map context = new HashMap();
            JsonLdOptions options = new JsonLdOptions();
            return JsonLdProcessor.compact(jsonObject, context, options);

        } catch (IOException | JsonLdError e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Return map containing schema.org info retrieved from the specified tool's descriptor cwl
     * @param id of specified tool
     * @return map containing schema.org info to be used as json-ld data
     */
    public Map findPublishedSchemaById(long id) {
        Tool tool = findPublishedById(id);
        Map<String, Object> map = new HashMap();

        if (tool != null) {
            String defaultVersion = tool.getDefaultVersion();

            String descriptorJson = getDescriptorJson(tool, defaultVersion);

            String schemaVariable = getSchemaVariable(descriptorJson);

            map = cwlJson2Map(descriptorJson, schemaVariable);

            // must be done after json has been converted to json-ld
            map = stripNamespace(map, schemaVariable);
        }

        return map;
    }

    /**
     * Return the tool's default version's descriptor json
     * @param tool specified by container ID
     * @param defaultVersion of tool
     * @return string which is the json equivalent of the tool's descriptor file
     */
    private String getDescriptorJson(final Tool tool, final String defaultVersion) {
        Tag defaultTag = null;

        for (Tag tag : tool.getTags()) {
            if (tag.getReference().equals(defaultVersion)) {
                defaultTag = tag;
                break;
            }
        }

        if (defaultTag != null) {
            for (SourceFile file : defaultTag.getSourceFiles()) {
                if (file.getType() == SourceFile.FileType.DOCKSTORE_CWL) {
                    return yaml2json(file.getContent()).replaceAll("\"", "\\\"");
                }
            }
        }

        return "";
    }


    /**
     * Returns the variable which refers to http://schema.org/
     * @param cwljson json converted from cwl document
     * @return string which refers to http://schema.org/
     */
    private String getSchemaVariable(String cwljson) {
        try {
            Map jsonObject = (Map) JsonUtils.fromString(cwljson);

            Map<String, String> namespaces = (Map<String, String>) jsonObject.get("$namespaces");

            for (Map.Entry<String, String> entry : namespaces.entrySet()) {
                if (entry.getValue().trim().equals("http://schema.org/")) {
                    return entry.getKey();
                }
            }

            return "";
        } catch (IOException e) {
            throw new RuntimeException("Could not create a map from input json. Please check if the following is valid json: " + cwljson);
        }
    }
}
