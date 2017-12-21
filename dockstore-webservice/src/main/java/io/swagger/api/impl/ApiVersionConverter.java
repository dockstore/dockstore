package io.swagger.api.impl;

import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.core.Response;

import io.swagger.model.Tool;
import io.swagger.model.ToolV1;
import io.swagger.model.ToolV2;

/**
 * @author gluu
 * @since 21/12/17
 */
public final class ApiVersionConverter {
    private ApiVersionConverter() { }

    public static Response convertToVersion(Response response, ApiVersion apiVersion) {

        List<Tool> object = (List<Tool>)response.getEntity();
        if (object instanceof List) {
            List<Object> newArrayList = new ArrayList<>();
            for (io.swagger.model.Tool innerObject : object) {
                if (innerObject instanceof io.swagger.model.Tool) {
                    if (apiVersion.equals(ApiVersion.v1)) {
                        ToolV1 toolV1 = new ToolV1();
                        toolV1.setTool(innerObject);
                        newArrayList.add(toolV1);
                    } else if (apiVersion.equals(ApiVersion.v2)) {
                        ToolV2 toolV2 = new ToolV2();
                        toolV2.setTool(innerObject);
                        newArrayList.add(toolV2);
                    }
                }
            }
            Response.ResponseBuilder responseBuilder = Response.ok(newArrayList);
            return responseBuilder.build();
        }
        return response;
    }

    public enum ApiVersion {
        v1, v2
    }
}
