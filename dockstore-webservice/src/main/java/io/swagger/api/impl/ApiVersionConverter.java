package io.swagger.api.impl;

import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.core.Response;

import io.swagger.model.Metadata;
import io.swagger.model.MetadataV1;
import io.swagger.model.MetadataV2;
import io.swagger.model.Tool;
import io.swagger.model.ToolV1;
import io.swagger.model.ToolV2;
import io.swagger.model.ToolVersion;
import io.swagger.model.ToolVersionV1;
import io.swagger.model.ToolVersionV2;

/**
 * @author gluu
 * @since 21/12/17
 */
public final class ApiVersionConverter {
    private ApiVersionConverter() { }

    public static Response convertToVersion(Response response, ApiVersion apiVersion) {

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
                }
                if (innerObject instanceof io.swagger.model.ToolVersion) {
                    ToolVersion toolVersion = (ToolVersion)innerObject;
                    if (apiVersion.equals(ApiVersion.v1)) {
                        newArrayList.add(getToolVersionV1(toolVersion));
                    } else if (apiVersion.equals(ApiVersion.v2)) {
                        newArrayList.add(getToolVersionV2(toolVersion));
                    } else {
                        handleError();
                    }
                }
            }
            Response.ResponseBuilder responseBuilder = Response.ok(newArrayList);
            return responseBuilder.build();
        } else if (object instanceof ToolVersion) {
            ToolVersion toolVersion = (ToolVersion)object;
            if (apiVersion.equals(ApiVersion.v1)) {
                ToolVersionV1 toolVersionV1 = getToolVersionV1(toolVersion);
                Response.ResponseBuilder responseBuilder = Response.ok(toolVersionV1);
                return responseBuilder.build();
            } else if (apiVersion.equals(ApiVersion.v2)) {
                ToolVersionV2 toolVersionV2 = getToolVersionV2(toolVersion);
                Response.ResponseBuilder responseBuilder = Response.ok(toolVersionV2);
                return responseBuilder.build();
            } else {
                handleError();
            }

        } else if (object instanceof Tool) {
            Tool tool = (Tool)object;
            if (apiVersion.equals(ApiVersion.v1)) {
                ToolV1 toolV1 = getToolV1(tool);
                Response.ResponseBuilder responseBuilder = Response.ok(toolV1);
                return responseBuilder.build();
            } else if (apiVersion.equals(ApiVersion.v2)) {
                ToolV2 toolV2 = getToolV2(tool);
                Response.ResponseBuilder responseBuilder = Response.ok(toolV2);
                return responseBuilder.build();
            } else {
                handleError();
            }
        } else if (object instanceof Metadata) {
            Metadata metadata = (Metadata) object;
            if (apiVersion.equals(ApiVersion.v1)) {
                MetadataV1 metadataV1 = getMetadataV1(metadata);
                Response.ResponseBuilder responseBuilder = Response.ok(metadataV1);
                return responseBuilder.build();
            } else if (apiVersion.equals(ApiVersion.v2)) {
                MetadataV2 metadataV2 = getMetadataV2(metadata);
                Response.ResponseBuilder responseBuilder = Response.ok(metadataV2);
                return responseBuilder.build();
            } else {
                handleError();
            }
        }
        return response;
    }

    private static ToolV1 getToolV1(Tool tool) {
        ToolV1 toolV1 = new ToolV1();
        toolV1.setTool(tool);
        return toolV1;
    }

    private static ToolV2 getToolV2(Tool tool) {
        ToolV2 toolV2 = new ToolV2();
        toolV2.setTool(tool);
        return toolV2;
    }

    private static ToolVersionV1 getToolVersionV1(ToolVersion toolVersion) {
        ToolVersionV1 toolVersionV1 = new ToolVersionV1();
        toolVersionV1.setToolVersion(toolVersion);
        return toolVersionV1;
    }

    private static ToolVersionV2 getToolVersionV2(ToolVersion toolVersion) {
        ToolVersionV2 toolVersionV2 = new ToolVersionV2();
        toolVersionV2.setToolVersion(toolVersion);
        return toolVersionV2;
    }

    private static MetadataV1 getMetadataV1(Metadata metadata) {
        MetadataV1 metadataV1 = new MetadataV1();
        metadataV1.setMetadata(metadata);
        return metadataV1;
    }

    private static MetadataV2 getMetadataV2(Metadata metadata) {
        MetadataV2 metadataV2 = new MetadataV2();
        metadataV2.setMetadata(metadata);
        return metadataV2;
    }


    public enum ApiVersion {
        v1, v2
    }

    private static void handleError() {
        throw new RuntimeException("Unknown");
    }
}
