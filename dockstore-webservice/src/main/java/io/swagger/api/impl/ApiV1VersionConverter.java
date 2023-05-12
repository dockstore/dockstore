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

import io.dockstore.common.DescriptorLanguage;
import io.dockstore.webservice.core.SourceFile;
import io.swagger.model.ExtendedFileWrapper;
import io.swagger.model.FileWrapper;
import io.swagger.model.Metadata;
import io.swagger.model.MetadataV1;
import io.swagger.model.Tool;
import io.swagger.model.ToolDescriptor;
import io.swagger.model.ToolDockerfile;
import io.swagger.model.ToolTestsV1;
import io.swagger.model.ToolV1;
import io.swagger.model.ToolVersion;
import io.swagger.model.ToolVersionV1;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;

/**
 * Converts between the V2-beta version of the GA4GH TRS to V1
 * @author gluu, dyuen
 * @since 21/12/17
 */
public final class ApiV1VersionConverter {
    private ApiV1VersionConverter() { }

    public static Response convertToVersion(Response response) {
        Object object = response.getEntity();
        if (object instanceof List) {
            List<Object> arrayList = (List<Object>)object;
            List<Object> newArrayList = new ArrayList<>();
            for (Object innerObject : arrayList) {
                if (innerObject instanceof Tool) {
                    Tool tool = (Tool)innerObject;
                    newArrayList.add(new ToolV1(tool));
                } else if (innerObject instanceof ToolVersion) {
                    ToolVersion toolVersion = (ToolVersion)innerObject;
                    newArrayList.add(new ToolVersionV1(toolVersion));
                } else if (innerObject instanceof ExtendedFileWrapper) {
                    // v1 annoying expects a 1 Dockerfile list to be returned unwrapped
                    Object extendedWrapperConverted = getExtendedWrapperConverted((ExtendedFileWrapper)innerObject);
                    if (arrayList.size() == 1 && ((ExtendedFileWrapper)innerObject).getOriginalFile().getType() == DescriptorLanguage.FileType.DOCKERFILE) {
                        return getResponse(extendedWrapperConverted, response.getHeaders());
                    }
                    newArrayList.add(extendedWrapperConverted);
                } else {
                    newArrayList.add(innerObject);
                }
            }
            return getResponse(newArrayList, response.getHeaders());
        } else if (object instanceof ToolVersion) {
            ToolVersion toolVersion = (ToolVersion)object;
            ToolVersionV1 toolVersionV1 =  new ToolVersionV1(toolVersion);
            return getResponse(toolVersionV1, response.getHeaders());
        } else if (object instanceof Tool) {
            Tool tool = (Tool)object;
            ToolV1 toolV1 = new ToolV1(tool);
            return getResponse(toolV1, response.getHeaders());
        } else if (object instanceof Metadata) {
            Metadata metadata = (Metadata)object;
            MetadataV1 metadataV1 = new MetadataV1(metadata);
            return getResponse(metadataV1, response.getHeaders());
        } else if (object instanceof FileWrapper) {
            if (object instanceof ExtendedFileWrapper) {
                return getResponse(getExtendedWrapperConverted((ExtendedFileWrapper)object), response.getHeaders());
            }
            return getResponse(object, response.getHeaders());
        }
        return response;
    }

    private static Object getExtendedWrapperConverted(ExtendedFileWrapper wrapper) {
        if (wrapper.getOriginalFile().getType() == DescriptorLanguage.FileType.DOCKERFILE) {
            return new ToolDockerfile(wrapper);
        } else if (SourceFile.TEST_FILE_TYPES.contains(wrapper.getOriginalFile().getType())) {
            return new ToolTestsV1(wrapper);
        } else {
            return new ToolDescriptor(wrapper);
        }
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
                    // Skipping all other headers
                }
            }
        }
        return responseBuilder.build();
    }

}
