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

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.Alias;
import io.dockstore.webservice.core.CollectionOrganization;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.helpers.ElasticManager;
import io.dockstore.webservice.helpers.ElasticMode;
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
public class EntryResource implements AuthenticatedResourceInterface {

    private static final Logger LOG = LoggerFactory.getLogger(EntryResource.class);
    private final ToolDAO toolDAO;
    private final ElasticManager elasticManager;

    public EntryResource(ObjectMapper mapper, ToolDAO toolDAO) {
        this.toolDAO = toolDAO;
        elasticManager = new ElasticManager();
    }

    @PUT
    @Timed
    @UnitOfWork
    @Path("/{id}/aliases")
    @ApiOperation(value = "Update the aliases linked to an entry.", authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, notes = "Aliases are alphanumerical (case-insensitive and may contain internal hyphens), given in a comma-delimited list.", response = Entry.class)
    public Entry updateAliases(@ApiParam(hidden = true) @Auth User user,
        @ApiParam(value = "Entry to modify.", required = true) @PathParam("id") Long id,
        @ApiParam(value = "Comma-delimited list of aliases.", required = true) @QueryParam("aliases") String aliases,
        @ApiParam(value = "This is here to appease Swagger. It requires PUT methods to have a body, even if it is empty. Please leave it empty.") String emptyBody) {
        Entry<? extends Entry, ? extends Version> c = toolDAO.getGenericEntryById(id);
        checkEntry(c);
        checkUserCanUpdate(user, c);
        // compute differences
        Set<String> oldAliases = c.getAliases().keySet();
        Set<String> newAliases = Sets.newHashSet(Arrays.stream(aliases.split(",")).map(String::trim).toArray(String[]::new));
        Set<String> aliasesToAdd = Sets.difference(newAliases, oldAliases);
        Set<String> aliasesToRemove = new TreeSet<>(Sets.difference(oldAliases, newAliases));
        // add new ones and remove old ones while retaining the old entries and their order
        aliasesToAdd.forEach(alias -> c.getAliases().put(alias, new Alias()));
        aliasesToRemove.forEach(alias -> c.getAliases().remove(alias));

        elasticManager.handleIndexUpdate(c, ElasticMode.UPDATE);
        return c;
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
        List<CollectionOrganization> collectionsByEntryId = this.toolDAO.findCollectionsByEntryId(entry.getId());
        return collectionsByEntryId;
    }

}
