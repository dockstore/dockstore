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
package io.swagger.api.impl;

import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import io.swagger.model.Metadata;
import io.swagger.model.MetadataV1;
import io.swagger.model.Tool;
import io.swagger.model.ToolV1;
import io.swagger.model.ToolVersion;
import io.swagger.model.ToolVersionV1;

/**
 * @author gluu
 * @since 21/12/17
 */
public final class ApiVersionConverter {
    private ApiVersionConverter() { }

    public static Response convertToVersion(Response response) {
        Object object = response.getEntity();
        if (object instanceof List) {
            List<Object> arrayList = (List<Object>)object;
            List<Object> newArrayList = new ArrayList<>();
            for (Object innerObject : arrayList) {
                if (innerObject instanceof Tool) {
                    Tool tool = (Tool)innerObject;
                    newArrayList.add(getToolV1(tool));
                } else {
                    if (innerObject instanceof io.swagger.model.ToolVersion) {
                        ToolVersion toolVersion = (ToolVersion)innerObject;
                        newArrayList.add(getToolVersionV1(toolVersion));
                    } else {
                        return getResponse(object, response.getHeaders());
                    }
                }
            }
            return getResponse(newArrayList, response.getHeaders());
        } else if (object instanceof ToolVersion) {
            ToolVersion toolVersion = (ToolVersion)object;
            ToolVersionV1 toolVersionV1 = getToolVersionV1(toolVersion);
            return getResponse(toolVersionV1, response.getHeaders());
        } else if (object instanceof Tool) {
            Tool tool = (Tool)object;
            ToolV1 toolV1 = getToolV1(tool);
            return getResponse(toolV1, response.getHeaders());

        } else if (object instanceof Metadata) {
            Metadata metadata = (Metadata)object;
            MetadataV1 metadataV1 = getMetadataV1(metadata);
            return getResponse(metadataV1, response.getHeaders());
        }
        return response;
    }

    private static ToolV1 getToolV1(Tool tool) {
        return new ToolV1(tool);
    }

    private static ToolVersionV1 getToolVersionV1(ToolVersion toolVersion) {
        return new ToolVersionV1(toolVersion);
    }

    private static Response getResponse(Object object, MultivaluedMap<String, Object> headers) {
        Response.ResponseBuilder responseBuilder = Response.ok(object);
        if (!headers.isEmpty()) {
            for (String str : headers.keySet()) {
                String newString = "";
                switch (str) {
                case "next_page":
                    newString = "next-page";
                    responseBuilder.header(newString, headers.getFirst(str));
                    break;
                case "last_page":
                    newString = "last-page";
                    responseBuilder.header(newString, headers.getFirst(str));
                    break;
                case "current_offset":
                    newString = "current-offset";
                    responseBuilder.header(newString, headers.getFirst(str));
                    break;
                case "current_limit":
                    newString = "current-limit";
                    responseBuilder.header(newString, headers.getFirst(str));
                    break;
                default:
                    continue; // Skipping all other headers
                }
            }
        }
        return responseBuilder.build();
    }

    private static MetadataV1 getMetadataV1(Metadata metadata) {
        return new MetadataV1(metadata);
    }

}
