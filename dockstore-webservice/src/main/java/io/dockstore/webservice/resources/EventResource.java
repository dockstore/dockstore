/*
 * Copyright 2019 OICR
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.dockstore.webservice.resources;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import com.codahale.metrics.annotation.Timed;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.Event;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.jdbi.EventDAO;
import io.dockstore.webservice.jdbi.UserDAO;
import io.dropwizard.auth.Auth;
import io.dropwizard.hibernate.UnitOfWork;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import static io.dockstore.webservice.Constants.JWT_SECURITY_DEFINITION_NAME;
import static io.dockstore.webservice.jdbi.EventDAO.PAGINATION_RANGE;

/**
 * @author gluu
 * @since 2019-12-05
 */
@Path("/events")
@Api("events")
@Produces(MediaType.APPLICATION_JSON)
public class EventResource {
    private static final String PAGINATION_DEFAULT_STRING = "10";
    private static final String SUMMARY = "Get events based on filters.";
    private static final String DESCRIPTION = "Optional authentication.";
    private final EventDAO eventDAO;
    private final UserDAO userDAO;
    public EventResource(EventDAO eventDAO, UserDAO userDAO) {
        this.eventDAO = eventDAO;
        this.userDAO = userDAO;
    }
    @SuppressWarnings("checkstyle:MagicNumber")
    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Operation(description = DESCRIPTION, summary = SUMMARY, security = @SecurityRequirement(name = "bearer"))
    @ApiOperation(value = SUMMARY, authorizations = {
            @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, notes = DESCRIPTION, responseContainer = "List", response = Event.class)
    // TODO: Add openapi annotation for pagination range
    public List<Event> getEvents(@ApiParam(hidden = true) @Auth User user, @QueryParam("event_search_type") EventSearchType eventSearchType, @Min(1) @Max(100) @DefaultValue(PAGINATION_DEFAULT_STRING) @ApiParam(defaultValue = PAGINATION_DEFAULT_STRING, allowableValues = PAGINATION_RANGE) @QueryParam("limit") Integer limit, @QueryParam("offset") @DefaultValue("0") Integer offset) {
        if (eventSearchType.equals(EventSearchType.STARRED_ENTRIES)) {
            User userWithSession = this.userDAO.findById(user.getId());
            Set<Long> entryIDs = userWithSession.getStarredEntries().stream().map(Entry::getId).collect(Collectors.toSet());
            return this.eventDAO.findEventsByEntryIDs(entryIDs, offset, limit);
        } else {
            return Collections.emptyList();
        }
    }
}


