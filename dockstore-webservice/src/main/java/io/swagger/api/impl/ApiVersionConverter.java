package io.swagger.api.impl;

import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import io.dockstore.webservice.CustomWebApplicationException;
import io.swagger.model.Metadata;
import io.swagger.model.MetadataV1;
import io.swagger.model.MetadataV2;
import io.swagger.model.Tool;
import io.swagger.model.ToolV1;
import io.swagger.model.ToolV2;
import io.swagger.model.ToolVersion;
import io.swagger.model.ToolVersionV1;
import io.swagger.model.ToolVersionV2;
import org.apache.http.HttpStatus;

/**
 * @author gluu
 * @since 21/12/17
 */
public final class ApiVersionConverter {
    private ApiVersionConverter() { }

    public static Response convertToVersion(Response response, ApiVersion apiVersion) {
        MultivaluedMap<String, Object> headers = response.getHeaders();
        Object object = response.getEntity();
        if (object instanceof List) {
            List<Object> arrayList = (List<Object>)object;
            List<Object> newArrayList = new ArrayList<>();
            for (Object innerObject : arrayList) {
                if (innerObject instanceof Tool) {
                    Tool tool = (Tool)innerObject;
                    if (apiVersion.equals(ApiVersion.v1)) {
                        newArrayList.add(getToolV1(tool));
                    } else if (apiVersion.equals(ApiVersion.v2)) {
                        newArrayList.add(getToolV2(tool));
                    } else {
                        handleError();
                    }
                } else {
                    if (innerObject instanceof io.swagger.model.ToolVersion) {
                        ToolVersion toolVersion = (ToolVersion)innerObject;
                        if (apiVersion.equals(ApiVersion.v1)) {
                            newArrayList.add(getToolVersionV1(toolVersion));
                        } else if (apiVersion.equals(ApiVersion.v2)) {
                            newArrayList.add(getToolVersionV2(toolVersion));
                        } else {
                            handleError();
                        }
                    } else {
                        return getResponse(object, response.getHeaders(), apiVersion);
                    }
                }
            }
            return getResponse(newArrayList, response.getHeaders(), apiVersion);
        } else if (object instanceof ToolVersion) {
            ToolVersion toolVersion = (ToolVersion)object;
            if (apiVersion.equals(ApiVersion.v1)) {
                ToolVersionV1 toolVersionV1 = getToolVersionV1(toolVersion);
                return getResponse(toolVersionV1, response.getHeaders(), apiVersion);
            } else if (apiVersion.equals(ApiVersion.v2)) {
                ToolVersionV2 toolVersionV2 = getToolVersionV2(toolVersion);
                return getResponse(toolVersionV2, response.getHeaders(), apiVersion);
            } else {
                handleError();
            }

        } else if (object instanceof Tool) {
            Tool tool = (Tool)object;
            if (apiVersion.equals(ApiVersion.v1)) {
                ToolV1 toolV1 = getToolV1(tool);
                return getResponse(toolV1, response.getHeaders(), apiVersion);
            } else if (apiVersion.equals(ApiVersion.v2)) {
                ToolV2 toolV2 = getToolV2(tool);
                return getResponse(toolV2, response.getHeaders(), apiVersion);
            } else {
                handleError();
            }
        } else if (object instanceof Metadata) {
            Metadata metadata = (Metadata)object;
            if (apiVersion.equals(ApiVersion.v1)) {
                MetadataV1 metadataV1 = getMetadataV1(metadata);
                return getResponse(metadataV1, response.getHeaders(), apiVersion);
            } else if (apiVersion.equals(ApiVersion.v2)) {
                MetadataV2 metadataV2 = getMetadataV2(metadata);
                return getResponse(metadataV2, response.getHeaders(), apiVersion);
            } else {
                handleError();
            }
        }
        return response;
    }

    private static ToolV1 getToolV1(Tool tool) {
        return new ToolV1(tool);
    }

    private static ToolV2 getToolV2(Tool tool) {
        return new ToolV2(tool);
    }

    private static ToolVersionV1 getToolVersionV1(ToolVersion toolVersion) {
        ToolVersionV1 toolVersionV1 = new ToolVersionV1(toolVersion);
        return toolVersionV1;
    }

    private static ToolVersionV2 getToolVersionV2(ToolVersion toolVersion) {
        ToolVersionV2 toolVersionV2 = new ToolVersionV2(toolVersion);
        return toolVersionV2;
    }

    private static Response getResponse(Object object, MultivaluedMap<String, Object> headers, ApiVersion apiVersion) {
        Response.ResponseBuilder responseBuilder = Response.ok(object);
        if (!headers.isEmpty()) {
            if (apiVersion.equals(ApiVersion.v2)) {
                for (String str : headers.keySet()) {
                    responseBuilder.header(str, headers.getFirst(str));
                }
            } else if (apiVersion.equals(ApiVersion.v1)) {
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
        }
        return responseBuilder.build();
    }

    private static MetadataV1 getMetadataV1(Metadata metadata) {
        return new MetadataV1(metadata);
    }

    private static MetadataV2 getMetadataV2(Metadata metadata) {
        return new MetadataV2(metadata);
    }

    private static void handleError() {
        throw new CustomWebApplicationException("Unknown response type", HttpStatus.SC_BAD_REQUEST);
    }

    public enum ApiVersion {
        v1, v2
    }
}
