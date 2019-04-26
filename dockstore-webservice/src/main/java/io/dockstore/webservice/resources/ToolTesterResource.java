/*
 *
 *  *    Copyright 2019 OICR
 *  *
 *  *    Licensed under the Apache License, Version 2.0 (the "License");
 *  *    you may not use this file except in compliance with the License.
 *  *    You may obtain a copy of the License at
 *  *
 *  *        http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  *    Unless required by applicable law or agreed to in writing, software
 *  *    distributed under the License is distributed on an "AS IS" BASIS,
 *  *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  *    See the License for the specific language governing permissions and
 *  *    limitations under the License.
 *
 */

package io.dockstore.webservice.resources;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.codahale.metrics.annotation.Timed;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.ToolTester.ToolTesterLog;
import io.dockstore.webservice.core.ToolTester.ToolTesterLogType;
import io.dockstore.webservice.core.ToolTester.ToolTesterS3Client;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.http.HttpStatus;

/**
 * @author gluu
 * @since 1.7.0
 */
@Path("/toolTester")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "toolTester", description = "Interactions with the Dockstore-support's ToolTester application")
public class ToolTesterResource {
    private final String bucketName;

    public ToolTesterResource(DockstoreWebserviceConfiguration configuration) {
        bucketName = configuration.getToolTesterBucket();
    }

    @GET
    @Timed
    @Path("logs")
    @Operation(summary = "Get ToolTester log file")
    @Produces(MediaType.TEXT_PLAIN)
    public String getToolTesterLog(@Parameter(in = ParameterIn.QUERY, name = "tool_id", example = "#workflow/github.com/dockstore/hello_world", required = true) String toolId,
            @Parameter(in = ParameterIn.QUERY, name = "tool_version_name", example = "v1.0.0", required = true) String toolVersionName,
            @Parameter(in = ParameterIn.QUERY, name = "test_filename", example = "hello_world.cwl.json", required = true) String testFilename,
            @Parameter(in = ParameterIn.QUERY, name = "runner", example = "cwltool", required = true) String runner,
            @Parameter(in = ParameterIn.QUERY, name = "log_type", required = true) ToolTesterLogType logType,
            @Parameter(in = ParameterIn.QUERY, name = "filename", example = "1554477737092.log", required = true) String filename) {
        if (bucketName == null) {
            throw new CustomWebApplicationException("Dockstore Logging integration is currently not set up",
                    HttpStatus.SC_SERVICE_UNAVAILABLE);
        }
        ToolTesterS3Client toolTesterS3Client = new ToolTesterS3Client(bucketName);
        try {
            return toolTesterS3Client.getToolTesterLog(toolId, toolVersionName, testFilename, runner, filename);
        } catch (AmazonS3Exception e) {
            throw new CustomWebApplicationException("Dockstore Logging integration is currently not set up",
                    HttpStatus.SC_SERVICE_UNAVAILABLE);
        } catch (IOException e) {
            throw new CustomWebApplicationException("Could not log file contents", HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @GET
    @Timed
    @Path("logs/search")
    @Operation(summary = "Search for ToolTester log files")
    public List<ToolTesterLog> search(@Parameter(in = ParameterIn.QUERY, name = "tool_id", example = "#workflow/github.com/dockstore/hello_world", required = true) String toolId,
            @Parameter(in = ParameterIn.QUERY, name = "tool_version_name", example = "v1.0.0", required = true) String toolVersionName) {
        if (bucketName == null) {
            throw new CustomWebApplicationException("Dockstore Logging integration is currently not set up",
                    HttpStatus.SC_SERVICE_UNAVAILABLE);
        }
        try {
            ToolTesterS3Client toolTesterS3Client = new ToolTesterS3Client(bucketName);
            return toolTesterS3Client.getToolTesterLogs(toolId, toolVersionName);
        } catch (AmazonS3Exception e) {
            throw new CustomWebApplicationException("Dockstore Logging integration is currently not set up",
                    HttpStatus.SC_SERVICE_UNAVAILABLE);
        } catch (UnsupportedEncodingException e) {
            throw new CustomWebApplicationException("Could generate s3 bucket key", HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }
}
