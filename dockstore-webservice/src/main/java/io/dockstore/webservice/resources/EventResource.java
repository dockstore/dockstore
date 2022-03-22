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

import static io.dockstore.webservice.Constants.JWT_SECURITY_DEFINITION_NAME;
import static io.dockstore.webservice.jdbi.EventDAO.MAX_LIMIT;
import static io.dockstore.webservice.jdbi.EventDAO.PAGINATION_RANGE;

import com.codahale.metrics.annotation.Timed;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.Event;
import io.dockstore.webservice.core.Organization;
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
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.hibernate.Hibernate;

/**
 * Avoid adding Swagger annotations to this, only use OpenAPI 3.0 annotations when possible.
 * The UI currently does not rely on Swagger.
 * TODO: Remove all Swagger annotations
 * @author gluu
 * @since 1.8.0
 */
@Path("/events")
@Api("events")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "events")
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

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Operation(description = DESCRIPTION, summary = SUMMARY, security = @SecurityRequirement(name = "bearer"))
    @ApiOperation(value = SUMMARY, authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME)}, notes = DESCRIPTION, responseContainer = "List", response = Event.class)
    public List<Event> getEvents(@Parameter(hidden = true) @ApiParam(hidden = true) @Auth User user, @QueryParam("event_search_type") EventSearchType eventSearchType, @Min(1) @Max(MAX_LIMIT) @DefaultValue(PAGINATION_DEFAULT_STRING) @ApiParam(defaultValue = PAGINATION_DEFAULT_STRING, allowableValues = PAGINATION_RANGE) @Parameter(schema = @Schema(maximum = "100", minimum = "1")) @QueryParam("limit") int limit, @QueryParam("offset") @DefaultValue("0") Integer offset) {
        User userWithSession = this.userDAO.findById(user.getId());
        switch (eventSearchType) {
        case STARRED_ENTRIES:
            Set<Long> entryIDs = userWithSession.getStarredEntries().stream().map(Entry::getId).collect(Collectors.toSet());
            List<Event> eventsByEntryIDs = this.eventDAO.findEventsByEntryIDs(entryIDs, offset, limit);
            eagerLoadEventEntries(eventsByEntryIDs);
            return eventsByEntryIDs;
        case STARRED_ORGANIZATION:
            Set<Long> organizationIDs = userWithSession.getStarredOrganizations().stream().map(Organization::getId).collect(Collectors.toSet());
            List<Event> allByOrganizationIds = this.eventDAO.findAllByOrganizationIds(organizationIDs, offset, limit);
            eagerLoadEventEntries(allByOrganizationIds);
            return allByOrganizationIds;
        case ALL_STARRED:
            Set<Long> organizationIDs2 = userWithSession.getStarredOrganizations().stream().map(Organization::getId).collect(Collectors.toSet());
            Set<Long> entryIDs2 = userWithSession.getStarredEntries().stream().map(Entry::getId).collect(Collectors.toSet());
            List<Event> allByOrganizationIdsOrEntryIds = this.eventDAO
                    .findAllByOrganizationIdsOrEntryIds(organizationIDs2, entryIDs2, offset, limit);
            eagerLoadEventEntries(allByOrganizationIdsOrEntryIds);
            return allByOrganizationIdsOrEntryIds;
        default:
            return Collections.emptyList();
        }
    }

    private void eagerLoadEventEntries(List<Event> events) {
        events.forEach(event -> {
            Hibernate.initialize(event.getUser());
            Hibernate.initialize(event.getOrganization());
            Hibernate.initialize(event.getTool());
            Hibernate.initialize(event.getWorkflow());
            Hibernate.initialize(event.getCollection());
            Hibernate.initialize(event.getInitiatorUser());
            Hibernate.initialize(event.getApptool());
        });
    }
}


