/*
 *    Copyright 2024 OICR, UCSC
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

package io.dockstore.webservice.resources;

import static io.dockstore.webservice.resources.ResourceConstants.JWT_SECURITY_DEFINITION_NAME;

import io.dockstore.common.HttpStatusMessageConstants;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.swagger.annotations.Api;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.security.SecuritySchemes;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import java.util.ArrayList;
import java.util.List;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/testing")
@Api("testing")
@Hidden
@SecuritySchemes({ @SecurityScheme(type = SecuritySchemeType.HTTP, name = JWT_SECURITY_DEFINITION_NAME, scheme = "bearer") })
@Tag(name = "testing", description = ResourceConstants.TESTING)
public class TestingResource {

    private static final Logger LOG = LoggerFactory.getLogger(TestingResource.class);
    private static final int MAXIMUM_MEMORY_CHUNK_SIZE = 16 * 1024 * 1024;
    private static final long MILLISECONDS_PER_SECOND = 1000L;

    private final DockstoreWebserviceConfiguration config;

    public TestingResource(DockstoreWebserviceConfiguration config) {
        this.config = config;
    }

    @POST
    @Path("/consume/memory/{byteCount}/{durationSeconds}")
    @RolesAllowed("admin")
    @Operation(summary = "Consume the specified amount of heap memory for the specified duration.", description = "Consume the specified amount of heap memory for the specified duration.")
    @ApiResponse(responseCode = HttpStatus.SC_OK + "", description = "A description of what was done")
    @ApiResponse(responseCode = HttpStatus.SC_BAD_REQUEST + "", description = HttpStatusMessageConstants.BAD_REQUEST)
    public String consumeResource(@PathParam("byteCount") long byteCount, @PathParam("durationSeconds") long durationSeconds) {
        checkEnvironment();

        String actionMessage = String.format("consuming %d bytes of memory for %d seconds", byteCount, durationSeconds);
        LOG.info(actionMessage);

        List<byte[]> chunks = new ArrayList<>();
        while (byteCount > 0) {
            byte[] chunk = new byte[(int)Math.min(byteCount, MAXIMUM_MEMORY_CHUNK_SIZE)];
            chunks.add(chunk);
            byteCount -= chunk.length;
        }

        sleepSeconds(durationSeconds);

        String doneMessage = "done " + actionMessage;
        LOG.info(doneMessage);
        return doneMessage;
    }

    private void checkEnvironment() {
        // Throw if we're running in production, ensuring that these endpoints cannot be used to crash it.
        if (config.getExternalConfig().computeIsProduction()) {
            throw new CustomWebApplicationException("endpoint cannot be invoked in production", HttpStatus.SC_BAD_REQUEST);
        }
    }

    private void sleepSeconds(long seconds) {
        try {
            Thread.sleep(seconds * MILLISECONDS_PER_SECOND);
        } catch (InterruptedException e) {
            // This space intentionally left blank.
        }
    }
}

