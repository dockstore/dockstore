/*
 *    Copyright 2018 OICR
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

import java.util.List;
import java.util.Optional;

import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import com.codahale.metrics.annotation.Timed;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.CollectionOrganization;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.helpers.ElasticManager;
import io.dockstore.webservice.jdbi.ToolDAO;
import io.dropwizard.auth.Auth;
import io.dropwizard.hibernate.UnitOfWork;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.dockstore.webservice.Constants.JWT_SECURITY_DEFINITION_NAME;

/**
 * Prototype for methods that apply identically across tools and workflows.
 *
 * @author dyuen
 */
@Path("/entries")
@Api("entries")
@Produces(MediaType.APPLICATION_JSON)
public class EntryResource implements AuthenticatedResourceInterface, AliasableResourceInterface<Entry> {

    private static final Logger LOG = LoggerFactory.getLogger(EntryResource.class);
    private final ToolDAO toolDAO;
    private final ElasticManager elasticManager;

    public EntryResource(ToolDAO toolDAO) {
        this.toolDAO = toolDAO;
        elasticManager = new ElasticManager();
    }

    @PUT
    @Timed
    @UnitOfWork
    @Override
    @Path("{id}/aliases")
    @ApiOperation(nickname = "updateAliases", value = "Update the aliases linked to a entry in Dockstore.", authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, notes = "Aliases are alphanumerical (case-insensitive and may contain internal hyphens), given in a comma-delimited list.", response = Entry.class)
    public Entry updateAliases(@ApiParam(hidden = true) @Auth User user,
        @ApiParam(value = "Entry to modify.", required = true) @PathParam("id") Long id,
        @ApiParam(value = "Comma-delimited list of aliases.", required = true) @QueryParam("aliases") String aliases,
        @ApiParam(value = "This is here to appease Swagger. It requires PUT methods to have a body, even if it is empty. Please leave it empty.") String emptyBody) {
        return AliasableResourceInterface.super.updateAliases(user, id, aliases, emptyBody);
    }

    @GET
    @Path("/{id}/collections")
    @Timed
    @UnitOfWork
    @ApiOperation(value = "Get the collections and organizations that contain the published entry", notes = "Entry must be published", response = CollectionOrganization.class, responseContainer = "List")
    public List<CollectionOrganization> entryCollections(@ApiParam(value = "id", required = true) @PathParam("id") Long id) {
        Entry<? extends Entry, ? extends Version> entry = toolDAO.getGenericEntryById(id);
        if (entry == null || !entry.getIsPublished()) {
            throw new CustomWebApplicationException("Published entry does not exist.", HttpStatus.SC_BAD_REQUEST);
        }
        return this.toolDAO.findCollectionsByEntryId(entry.getId());
    }

    @Override
    public Optional<ElasticManager> getElasticManager() {
        return Optional.of(elasticManager);
    }

    @Override
    public Entry getAndCheckResource(User user, Long id) {
        Entry<? extends Entry, ? extends Version> c = toolDAO.getGenericEntryById(id);
        checkEntry(c);
        checkUserCanUpdate(user, c);
        return c;
    }
}
