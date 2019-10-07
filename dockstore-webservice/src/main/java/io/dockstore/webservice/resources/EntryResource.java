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

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Optional;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import com.codahale.metrics.annotation.Timed;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.BioWorkflow;
import io.dockstore.webservice.core.CollectionOrganization;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.Service;
import io.dockstore.webservice.core.Tool;
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
import io.swagger.discourse.client.ApiClient;
import io.swagger.discourse.client.ApiException;
import io.swagger.discourse.client.Configuration;
import io.swagger.discourse.client.api.TopicsApi;
import io.swagger.discourse.client.model.InlineResponse2005;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.security.SecuritySchemes;
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
@SecuritySchemes({ @SecurityScheme(type = SecuritySchemeType.HTTP, name = "bearer", scheme = "bearer", bearerFormat = "JWT") })
public class EntryResource implements AuthenticatedResourceInterface, AliasableResourceInterface<Entry> {

    private static final Logger LOG = LoggerFactory.getLogger(EntryResource.class);

    private final ToolDAO toolDAO;
    private final ElasticManager elasticManager;
    private final TopicsApi topicsApi;
    private final String discourseKey;
    private final String discourseUrl;
    private final int discourseCategoryId;
    private final String discourseApiUsername = "system";
    private final int maxDescriptionLength = 500;

    public EntryResource(ToolDAO toolDAO, DockstoreWebserviceConfiguration configuration) {
        this.toolDAO = toolDAO;
        elasticManager = new ElasticManager();

        discourseUrl = configuration.getDiscourseUrl();
        discourseKey = configuration.getDiscourseKey();
        discourseCategoryId = configuration.getDiscourseCategoryId();

        ApiClient apiClient = Configuration.getDefaultApiClient();
        apiClient.addDefaultHeader("Content-Type", "application/x-www-form-urlencoded");
        apiClient.addDefaultHeader("cache-control", "no-cache");
        apiClient.setBasePath(discourseUrl);

        topicsApi = new TopicsApi(apiClient);
    }

    @POST
    @Timed
    @UnitOfWork
    @Override
    @Path("/{id}/aliases")
    @ApiOperation(nickname = "updateAliases", value = "Update the aliases linked to a entry in Dockstore.", authorizations = {
        @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, notes = "Aliases are alphanumerical (case-insensitive and may contain internal hyphens), given in a comma-delimited list.", response = Entry.class)
    public Entry addAliases(@ApiParam(hidden = true) @Auth User user,
                               @ApiParam(value = "Entry to modify.", required = true) @PathParam("id") Long id,
                               @ApiParam(value = "Comma-delimited list of aliases.", required = true) @QueryParam("aliases") String aliases,
                               @ApiParam(value = "This is here to appease Swagger. It requires PUT methods to have a body, even if it is empty. Please leave it empty.") String emptyBody) {
        return AliasableResourceInterface.super.addAliases(user, id, aliases, emptyBody);
    }

    @GET
    @Path("/{id}/collections")
    @Timed
    @UnitOfWork(readOnly = true)
    @ApiOperation(value = "Get the collections and organizations that contain the published entry", notes = "Entry must be published", response = CollectionOrganization.class, responseContainer = "List")
    public List<CollectionOrganization> entryCollections(@ApiParam(value = "id", required = true) @PathParam("id") Long id) {
        Entry<? extends Entry, ? extends Version> entry = toolDAO.getGenericEntryById(id);
        if (entry == null || !entry.getIsPublished()) {
            throw new CustomWebApplicationException("Published entry does not exist.", HttpStatus.SC_BAD_REQUEST);
        }
        return this.toolDAO.findCollectionsByEntryId(entry.getId());
    }

    @POST
    @Path("/{id}/topic")
    @Timed
    @RolesAllowed({ "curator", "admin" })
    @UnitOfWork
    @ApiOperation(value = "Create a discourse topic for an entry.", authorizations = {
            @Authorization(value = JWT_SECURITY_DEFINITION_NAME) }, response = Entry.class)
    @Operation(description = "Create a discourse topic for an entry.", security = @SecurityRequirement(name = "bearer"))
    public Entry setDiscourseTopic(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user", in = ParameterIn.HEADER) @Auth User user,
            @ApiParam(value = "The id of the entry to add a topic to.", required = true)
            @Parameter(description = "The id of the entry to add a topic to.", name = "id", in = ParameterIn.PATH, required = true)
            @PathParam("id") Long id) {
        return createAndSetDiscourseTopic(id);
    }

    /**
     * For a given entry, create a Discourse thread if applicable and set in database
     * @param id entry id
     * @return Entry with discourse ID set
     */
    public Entry createAndSetDiscourseTopic(Long id) throws CustomWebApplicationException {
        Entry entry = this.toolDAO.getGenericEntryById(id);

        if (entry == null || !entry.getIsPublished()) {
            throw new CustomWebApplicationException("Entry " + id + " does not exist or is not published.", HttpStatus.SC_NOT_FOUND);
        }

        if (entry.getTopicId() != null) {
            throw new CustomWebApplicationException("Entry " + id + " already has an associated Discourse topic.", HttpStatus.SC_BAD_REQUEST);
        }

        // Verify and set category
        Integer category = discourseCategoryId;

        // Create title and link to entry
        String entryLink = "https://dockstore.org/";
        String title;
        if (entry instanceof BioWorkflow) {
            title = ((BioWorkflow)(entry)).getWorkflowPath();
            entryLink += "workflows/";
        } else if (entry instanceof Service) {
            title = ((Service)(entry)).getWorkflowPath();
            entryLink += "services/";
        } else {
            title = ((Tool)(entry)).getToolPath();
            entryLink += "tools/";
        }

        entryLink += title;

        // Create description
        String description = "";
        if (entry.getDescription() != null) {
            description = entry.getDescription() != null ? entry.getDescription().substring(0, Math.min(entry.getDescription().length(), maxDescriptionLength)) : "";
        }

        description += "\n<hr>\n<small>This is a companion discussion topic for the original entry at <a href='" + entryLink + "'>" + title + "</a></small>\n";

        // Check that discourse is reachable
        boolean isReachable;
        HttpURLConnection connection = null;
        try {
            URL url = new URL(discourseUrl);
            connection = (HttpURLConnection)url.openConnection();
            connection.setRequestMethod("GET");
            connection.connect();
            int respCode = connection.getResponseCode();
            isReachable = respCode == HttpStatus.SC_OK;
        } catch (IOException ex) {
            LOG.error("Error reaching " + discourseUrl, ex);
            isReachable = false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        if (isReachable) {
            // Create a discourse topic
            InlineResponse2005 response;
            try {
                response = topicsApi.postsJsonPost(description, discourseKey, discourseApiUsername, title, null, category, null, null, null);
                entry.setTopicId(response.getId().longValue());
            } catch (ApiException ex) {
                String message = "Could not add a topic to the given entry.";
                LOG.error(message, ex);
                throw new CustomWebApplicationException(message, HttpStatus.SC_BAD_REQUEST);
            }
        }

        return entry;
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

    @Override
    public Entry getAndCheckResourceByAlias(String alias) {
        throw new UnsupportedOperationException("Use the TRS API for tools and workflows");
    }
}
