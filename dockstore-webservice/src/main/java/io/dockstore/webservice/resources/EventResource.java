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
import io.dropwizard.auth.Auth;
import io.dropwizard.hibernate.UnitOfWork;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;
import org.hibernate.SessionFactory;

import static io.dockstore.webservice.Constants.JWT_SECURITY_DEFINITION_NAME;

enum EventSearchType  {
    STARRED_ENTRIES
}

/**
 * @author gluu
 * @since 2019-12-05
 */
@Path("/events")
@Api("events")
@Produces(MediaType.APPLICATION_JSON)
public class EventResource {
    private final EventDAO eventDAO;

    public EventResource(SessionFactory sessionFactory) {
        this.eventDAO = new EventDAO(sessionFactory);
    }
    @GET
    @Timed
    @UnitOfWork
    @ApiOperation(value = "Refresh one particular tool.", authorizations = {
            @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, notes = "Optional authentication", responseContainer = "List", response = Event.class)
    public List<Event> getEvents(@ApiParam(hidden = true) @Auth User user, @QueryParam("event_search_type") EventSearchType eventSearchType) {
        if (eventSearchType.equals(EventSearchType.STARRED_ENTRIES)) {
            Set<Long> entryIDs = user.getStarredEntries().stream().map(Entry::getId).collect(Collectors.toSet());
            return this.eventDAO.findEventsByEntryIDs(entryIDs);
        } else {
            return Collections.emptyList();
        }
    }
}


