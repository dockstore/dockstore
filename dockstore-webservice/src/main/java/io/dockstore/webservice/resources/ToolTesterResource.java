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

import com.codahale.metrics.annotation.Timed;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.tooltester.ToolTesterLog;
import io.dockstore.webservice.core.tooltester.ToolTesterLogType;
import io.dockstore.webservice.core.tooltester.ToolTesterS3Client;
import io.swagger.annotations.Api;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.List;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.awscore.exception.AwsServiceException;

/**
 * @author gluu
 * @since 1.7.0
 */
@Api("/toolTester")
@Path("/toolTester")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "toolTester", description = ResourceConstants.TOOLTESTER)
public class ToolTesterResource {
    private static final Logger LOG = LoggerFactory.getLogger(ToolTesterResource.class);
    private final String bucketName;

    public ToolTesterResource(DockstoreWebserviceConfiguration configuration) {
        bucketName = configuration.getToolTesterBucket();
    }

    @GET
    @Timed
    @Path("logs")
    @Operation(summary = "Get ToolTester log file")
    @Produces(MediaType.TEXT_PLAIN)
    public String getToolTesterLog(
            @QueryParam("tool_id") @Parameter(description = "TRS Tool Id", example = "#workflow/github.com/dockstore/hello_world", required = true) String toolId,
            @QueryParam("tool_version_name") @Parameter(example = "v1.0.0", required = true) String toolVersionName,
            @QueryParam("test_filename") @Parameter(example = "hello_world.cwl.json", required = true) String testFilename,
            @QueryParam("runner") @Parameter(example = "cwltool", required = true) String runner,
            @QueryParam("log_type") @Parameter(required = true) ToolTesterLogType logType,
            @QueryParam("filename") @Parameter(example = "1554477737092.log", required = true) String filename) {
        if (this.bucketName == null) {
            throw new CustomWebApplicationException("Dockstore Logging integration is currently not set up",
                    HttpStatus.SC_SERVICE_UNAVAILABLE);
        }
        ToolTesterS3Client toolTesterS3Client = new ToolTesterS3Client(this.bucketName);
        try {
            return toolTesterS3Client.getToolTesterLog(toolId, toolVersionName, testFilename, runner, filename);
        } catch (AwsServiceException e) {
            LOG.error(e.getMessage(), e);
            throw new CustomWebApplicationException("Dockstore Logging integration is currently not set up",
                    HttpStatus.SC_SERVICE_UNAVAILABLE);
        } catch (IOException e) {
            LOG.error(e.getMessage(), e);
            throw new CustomWebApplicationException("Could not fetch log file contents", HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @GET
    @Timed
    @Path("logs/search")
    @Operation(summary = "Search for ToolTester log files")
    public List<ToolTesterLog> search(
            @QueryParam("tool_id") @Parameter(description = "TRS Tool Id", example = "#workflow/github.com/dockstore/hello_world", required = true) String toolId,
            @QueryParam("tool_version_name") @Parameter(example = "v1.0.0", required = true) String toolVersionName) {
        if (this.bucketName == null) {
            throw new CustomWebApplicationException("Dockstore Logging integration is currently not set up",
                    HttpStatus.SC_SERVICE_UNAVAILABLE);
        }
        try {
            ToolTesterS3Client toolTesterS3Client = new ToolTesterS3Client(this.bucketName);
            return toolTesterS3Client.getToolTesterLogs(toolId, toolVersionName);
        } catch (AwsServiceException e) {
            LOG.error(e.getMessage(), e);
            throw new CustomWebApplicationException("Dockstore Logging integration is currently not set up",
                    HttpStatus.SC_SERVICE_UNAVAILABLE);
        }
    }
}
