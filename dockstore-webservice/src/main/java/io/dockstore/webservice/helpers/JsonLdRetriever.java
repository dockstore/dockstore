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
package io.dockstore.webservice.helpers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.github.jsonldjava.core.JsonLdError;
import com.github.jsonldjava.core.JsonLdOptions;
import com.github.jsonldjava.core.JsonLdProcessor;
import com.github.jsonldjava.utils.JsonUtils;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.Tool;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Created by kcao on 20/03/17.
 */
public final class JsonLdRetriever {

    private static final String SCHEMA = "http://schema.org/";

    private JsonLdRetriever() { }

    private static String yaml2json(String yaml) {
        String json = "";

        try {
            ObjectMapper yamlReader = new ObjectMapper(new YAMLFactory());
            Object yamlObj = yamlReader.readValue(yaml, Object.class);
            ObjectMapper jsonWriter = new ObjectMapper();
            json = jsonWriter.writeValueAsString(yamlObj);
        } catch (IOException e) {
            throw new RuntimeException("Issue converting yaml to json", e);
        }

        return json;
    }

    /**
     * Strip out properties to only get schema objects
     * @param schemaMap properly annotated json-ld map with properties still attached
     * @return list of schema objects without properties
     */
    private static List getSchemaObjectsList(final Map<String, Object> schemaMap) {
        List schemaObjects = new ArrayList();
        for (Map.Entry<String, Object> entry : schemaMap.entrySet()) {
            Object value = entry.getValue();

            if (value instanceof Map) {
                schemaObjects.add(value);
            } else if (value instanceof List) {
                schemaObjects.addAll((List) value);
            }
        }
        return schemaObjects;
    }

    /**
     * Add @context to json-ld map and leaves out any schema properties which do not have a class
     * @param schemaMap json-ld map
     * @param schemaVariable string which refers to http://schema.org/
     * @return a map that has been properly annotated with @context
     */
    private static Map<String, Object> addContext(final Map<String, Object> schemaMap, final String schemaVariable) {
        Map<String, Object> merged = new HashMap<>(schemaMap);

        for (Map.Entry<String, Object> entry : schemaMap.entrySet()) {
            String key = entry.getKey();
            Object copyValue = merged.get(key);

            if (key.startsWith(schemaVariable)) {

                if (copyValue instanceof List) {
                    for (Iterator<Object> iterator = ((List) copyValue).iterator(); iterator.hasNext();) {
                        Object node = iterator.next();

                        if (node instanceof Map) {
                            if (((Map) node).get("@type") == null) {
                                iterator.remove();
                            } else {
                                ((Map) node).put("@context", SCHEMA);
                            }
                        }
                    }
                } else if (copyValue instanceof Map) {
                    if (((Map) copyValue).get("@type") == null) {
                        merged.remove(key);
                    } else {
                        ((Map) copyValue).put("@context", SCHEMA);
                    }
                }
            }
        }

        return merged;
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
            //Object value = entry.getValue();
            Object copyValue = merged.get(key);

            if (copyValue instanceof List) {
                for (int i = 0; i < ((List) copyValue).size(); i++) {
                    Object node = ((List) copyValue).get(i);

                    if (node instanceof Map) {
                        ((List) copyValue).set(i, addType((Map<String, Object>) node, schemaVariable));
                    }
                }
            } else if (copyValue instanceof Map) {
                merged.replace(key, copyValue, addType((Map) copyValue, schemaVariable));
            } else if (copyValue instanceof String) {
                String type = ((String) copyValue).replace(schemaVariable + ":", "");

                if ("class".equals(key) && ((String) copyValue).startsWith(schemaVariable)) {
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
                for (int i = 0; i < ((List) value).size(); i++) {
                    Object node = ((List) value).get(i);

                    if (node instanceof Map) {
                        ((List) copyValue).set(i, stripNamespace((Map<String, Object>) node, schemaVariable));
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
     * Return the tool's default version's descriptor json
     * @param tool specified by container ID
     * @param defaultVersion of tool
     * @return string which is the json equivalent of the tool's descriptor file
     */
    private static String getDescriptorJson(final Tool tool, final String defaultVersion) {
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
    private static String getSchemaVariable(String cwljson) {
        try {
            if (!cwljson.isEmpty()) {
                Map jsonObject = (Map) JsonUtils.fromString(cwljson);

                Map<String, String> namespaces = (Map<String, String>) jsonObject.get("$namespaces");

                if (namespaces != null) {
                    for (Map.Entry<String, String> entry : namespaces.entrySet()) {
                        if (entry.getValue().trim().equals(SCHEMA)) {
                            return entry.getKey();
                        }
                    }
                }
            }

            return "";
        } catch (IOException e) {
            throw new RuntimeException("Could not create a map from input json. Please check if the following is valid json: " + cwljson);
        }
    }

    /**
     * Return map containing schema.org info retrieved from the specified tool's descriptor cwl
     * @param tool specified tool
     * @return map containing schema.org info to be used as json-ld data
     */
    public static List getSchema(Tool tool) {
        List schemaObjects = new ArrayList();

        if (tool != null) {
            String defaultVersion = tool.getDefaultVersion();

            String descriptorJson = getDescriptorJson(tool, defaultVersion);

            String schemaVariable = getSchemaVariable(descriptorJson);

            if (!schemaVariable.isEmpty()) {
                Map<String, Object> map = cwlJson2Map(descriptorJson, schemaVariable);

                // must be done after json has been converted to json-ld
                map = addContext(map, schemaVariable);
                map = stripNamespace(map, schemaVariable);

                schemaObjects = getSchemaObjectsList(map);
            }
        }

        return schemaObjects;
    }
}
