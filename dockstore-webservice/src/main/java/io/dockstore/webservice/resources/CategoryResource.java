/*
 * Copyright 2021 OICR and UCSC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.dockstore.webservice.resources;

import com.codahale.metrics.annotation.Timed;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.Category;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.EntryVersion;
import io.dockstore.webservice.core.Event;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.helpers.ParamHelper;
import io.dockstore.webservice.helpers.PublicStateManager;
import io.dockstore.webservice.helpers.StateManagerMode;
import io.dockstore.webservice.jdbi.CategoryDAO;
import io.dockstore.webservice.jdbi.EventDAO;
import io.dockstore.webservice.jdbi.ToolDAO;
import io.dockstore.webservice.jdbi.VersionDAO;
import io.dockstore.webservice.jdbi.WorkflowDAO;
import io.dropwizard.auth.Auth;
import io.dropwizard.hibernate.UnitOfWork;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.security.SecuritySchemes;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.http.HttpStatus;
import org.hibernate.Hibernate;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Category endpoints
 */
@Path("/categories")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "categories", description = ResourceConstants.CATEGORIES)
@SecuritySchemes({@SecurityScheme(type = SecuritySchemeType.HTTP, name = ResourceConstants.JWT_SECURITY_DEFINITION_NAME, scheme = "bearer")})
public class CategoryResource implements AuthenticatedResourceInterface {

    private static final Logger LOG = LoggerFactory.getLogger(CategoryResource.class);

    private final CategoryDAO categoryDAO;
    private final CollectionHelper collectionHelper;
    private final WorkflowDAO workflowDAO;
    private final EventDAO eventDAO;

    public CategoryResource(SessionFactory sessionFactory) {
        this.categoryDAO = new CategoryDAO(sessionFactory);
        this.collectionHelper = new CollectionHelper(sessionFactory, new ToolDAO(sessionFactory), new VersionDAO(sessionFactory));
        this.workflowDAO = new WorkflowDAO(sessionFactory);
        this.eventDAO = new EventDAO(sessionFactory);
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Operation(operationId = "getCategories", summary = "Retrieve all categories.", description = "Retrieve all categories.")
    @ApiResponse(responseCode = HttpStatus.SC_OK + "", description = "Successfully retrieved categories", content = @Content(mediaType = MediaType.APPLICATION_JSON, array = @ArraySchema(schema = @Schema(implementation = Category.class))))
    public List<Category> getCategories(
        @Parameter(description = "Name of category to retrieve", name = "name", in = ParameterIn.QUERY, required = false) @QueryParam("name") String name,
        @Parameter(description = "Comma-delimited list of fields to include: entries", name = "include", in = ParameterIn.QUERY, required = false) @QueryParam("include") String include,
        @Parameter(description = "Maximum number of results to return", name = "limit", in = ParameterIn.QUERY, required = false, schema = @Schema(maximum = "100", minimum = "1", defaultValue = "10")) @Min(1) @Max(ResourceConstants.MAX_PAGINATION_LIMIT) @DefaultValue("10") @QueryParam("limit") int limit,
        @Parameter(description = "Offset of the first result to return", name = "offset", in = ParameterIn.QUERY, required = false, schema = @Schema(minimum = "0", defaultValue = "0")) @Min(0) @DefaultValue("0") @QueryParam("offset") int offset) {
        List<Category> categories;
        if (name != null) {
            Category category = categoryDAO.findByName(name);
            categories = (category != null) ? Arrays.asList(category) : Collections.emptyList();
        } else {
            categories = categoryDAO.getCategories(offset, limit);
        }

        boolean includeEntries = ParamHelper.csvIncludesField(include, "entries");
        categories.forEach(category -> {
            if (includeEntries) {
                collectionHelper.evictAndAddEntries(category);
            } else {
                collectionHelper.evictAndSummarize(category);
            }
        });

        return categories;
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/{id}")
    @Operation(operationId = "getCategoryById", summary = "Retrieve a category by ID.", description = "Retrieve a category by ID.")
    @ApiResponse(responseCode = HttpStatus.SC_OK + "", description = "Successfully retrieved category", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = Category.class)))
    @ApiResponse(responseCode = HttpStatus.SC_NOT_FOUND + "", description = "Category not found")
    public Category getCategoryByName(@Parameter(description = "Category ID.", name = "id", in = ParameterIn.PATH, required = true) @PathParam("id") Long id) {
        Category category = categoryDAO.findById(id);
        collectionHelper.throwExceptionForNullCollection(category);
        Hibernate.initialize(category.getAliases());
        collectionHelper.evictAndAddEntries(category);
        return (category);
    }

    @DELETE
    @Timed
    @UnitOfWork
    @Path("/{categoryId}/entry")
    @Operation(operationId = "removeAiCuratedEntryFromCategory", summary = "Delete an AI-curated entry from a category.", description = "Delete an AI-curated entry from a category. The entry must have been added to the category by AI. Only the owner of the entry may perform this action.", security = @SecurityRequirement(name = ResourceConstants.JWT_SECURITY_DEFINITION_NAME))
    @ApiResponse(responseCode = HttpStatus.SC_OK + "", description = "Successfully deleted AI-curated entry from category", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = Category.class)))
    @ApiResponse(responseCode = HttpStatus.SC_NOT_FOUND + "", description = "Category or entry not found")
    @ApiResponse(responseCode = HttpStatus.SC_FORBIDDEN + "", description = "User is not an owner of the entry, or the entry was not added to the category by AI")
    public Category removeAiCuratedEntryFromCategory(@Parameter(hidden = true, name = "user") @Auth User user,
        @Parameter(description = "Category ID.", name = "categoryId", in = ParameterIn.PATH, required = true) @PathParam("categoryId") Long categoryId,
        @Parameter(description = "Entry ID.", name = "entryId", in = ParameterIn.QUERY, required = true) @QueryParam("entryId") Long entryId) {

        Entry<? extends Entry, ? extends Version> entry = getPublishedEntry(entryId);
        Category category = getCategory(categoryId);
        checkIsOwner(user, entry);
        getAiCuratedEntryVersion(category, entry);

        category.removeEntry(entry.getId(), null);

        Event removeEvent = entry.getEventBuilder()
            .withOrganization(category.getOrganization())
            .withCategory(category)
            .withInitiatorUser(user)
            .withType(Event.EventType.REMOVE_FROM_CATEGORY)
            .build();
        eventDAO.create(removeEvent);

        PublicStateManager.getInstance().handleIndexUpdate(entry, StateManagerMode.UPDATE);

        return categoryDAO.findById(categoryId);
    }

    @PUT
    @Timed
    @UnitOfWork
    @Path("/{categoryId}/entry")
    @Operation(operationId = "approveAiCuratedEntryInCategory", summary = "Approve an AI-curated entry in a category.", description = "Approve an AI-curated entry in a category, changing its curator from AI to human. The entry must have been added to the category by AI. Only the owner of the entry may perform this action.", security = @SecurityRequirement(name = ResourceConstants.JWT_SECURITY_DEFINITION_NAME))
    @ApiResponse(responseCode = HttpStatus.SC_OK + "", description = "Successfully approved AI-curated entry in category", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = Category.class)))
    @ApiResponse(responseCode = HttpStatus.SC_NOT_FOUND + "", description = "Category or entry not found")
    @ApiResponse(responseCode = HttpStatus.SC_FORBIDDEN + "", description = "User is not an owner of the entry, or the entry was not added to the category by AI")
    public Category approveAiCuratedEntryInCategory(@Parameter(hidden = true, name = "user") @Auth User user,
        @Parameter(description = "Category ID.", name = "categoryId", in = ParameterIn.PATH, required = true) @PathParam("categoryId") Long categoryId,
        @Parameter(description = "Entry ID.", name = "entryId", in = ParameterIn.QUERY, required = true) @QueryParam("entryId") Long entryId,
        @Parameter(description = "This is here to appease Swagger. It requires PUT methods to have a body, even if it is empty. Please leave it empty.", name = "emptyBody") String emptyBody) {

        Entry<? extends Entry, ? extends Version> entry = getPublishedEntry(entryId);
        Category category = getCategory(categoryId);
        checkIsOwner(user, entry);
        EntryVersion entryVersion = getAiCuratedEntryVersion(category, entry);

        entryVersion.setCurator(EntryVersion.Curator.USER);

        Event approveEvent = entry.getEventBuilder()
            .withOrganization(category.getOrganization())
            .withCategory(category)
            .withInitiatorUser(user)
            .withType(Event.EventType.APPROVE_IN_CATEGORY)
            .build();
        eventDAO.create(approveEvent);

        PublicStateManager.getInstance().handleIndexUpdate(entry, StateManagerMode.UPDATE);

        return categoryDAO.findById(categoryId);
    }

    private Entry<? extends Entry, ? extends Version> getPublishedEntry(Long entryId) {
        Entry<? extends Entry, ? extends Version> entry = workflowDAO.getGenericEntryById(entryId);
        if (entry == null || !entry.getIsPublished()) {
            String msg = "Entry not found.";
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_BAD_REQUEST);
        }
        return entry;
    }

    private Category getCategory(Long categoryId) {
        Category category = categoryDAO.findById(categoryId);
        if (category == null) {
            String msg = "Category not found.";
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_NOT_FOUND);
        }
        return category;
    }

    private EntryVersion getAiCuratedEntryVersion(Category category, Entry<? extends Entry, ? extends Version> entry) {
        EntryVersion entryVersion = category.getEntry(entry.getId(), null)
            .orElseThrow(() -> new CustomWebApplicationException("Entry is not a member of this category.", HttpStatus.SC_FORBIDDEN));
        if (entryVersion.getCurator() != EntryVersion.Curator.AI) {
            throw new CustomWebApplicationException("Entry was not added to this category by AI.", HttpStatus.SC_FORBIDDEN);
        }
        return entryVersion;
    }
}
