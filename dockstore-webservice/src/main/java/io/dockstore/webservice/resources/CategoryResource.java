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
import io.dockstore.webservice.core.Category;
import io.dockstore.webservice.helpers.ParamHelper;
import io.dockstore.webservice.jdbi.CategoryDAO;
import io.dockstore.webservice.jdbi.ToolDAO;
import io.dropwizard.hibernate.UnitOfWork;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import org.apache.http.HttpStatus;
import org.hibernate.Hibernate;
import org.hibernate.SessionFactory;

/**
 * Category endpoints
 */
@Path("/categories")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "categories", description = ResourceConstants.CATEGORIES)
public class CategoryResource implements AuthenticatedResourceInterface {

    private final CategoryDAO categoryDAO;
    private final CollectionHelper collectionHelper;

    public CategoryResource(SessionFactory sessionFactory) {
        this.categoryDAO = new CategoryDAO(sessionFactory);
        this.collectionHelper = new CollectionHelper(sessionFactory, new ToolDAO(sessionFactory));

    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Operation(operationId = "getCategories", summary = "Retrieve all categories.", description = "Retrieve all categories.")
    @ApiResponse(responseCode = HttpStatus.SC_OK + "", description = "Successfully retrieved categories", content = @Content(mediaType = MediaType.APPLICATION_JSON, array = @ArraySchema(schema = @Schema(implementation = Category.class))))
    public List<Category> getCategories(
        @Parameter(description = "Name of category to retrieve", name = "name", in = ParameterIn.QUERY, required = false) @QueryParam("name") String name,
        @Parameter(description = "Comma-delimited list of fields to include: entries", name = "include", in = ParameterIn.QUERY, required = false) @QueryParam("include") String include) {
        List<Category> categories;
        if (name != null) {
            Category category = categoryDAO.findByName(name);
            categories = (category != null) ? Arrays.asList(category) : Collections.emptyList();
        } else {
            categories = categoryDAO.getCategories();
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
}
