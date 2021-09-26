package io.dockstore.webservice.resources;

import com.codahale.metrics.annotation.Timed;
import io.dockstore.webservice.core.Category;
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
import java.util.List;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
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
// @SecuritySchemes({ @SecurityScheme(type = SecuritySchemeType.HTTP, name = "bearer", scheme = "bearer") })
public class CategoryResource implements AuthenticatedResourceInterface {

    private static final Logger LOG = LoggerFactory.getLogger(CategoryResource.class);

    private final SessionFactory sessionFactory;
    private final CategoryDAO categoryDAO;
    private final ToolDAO toolDAO;
    private final CollectionHelper collectionHelper;

    public CategoryResource(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
        this.categoryDAO = new CategoryDAO(sessionFactory);
        this.toolDAO = new ToolDAO(sessionFactory);
        this.collectionHelper = new CollectionHelper(sessionFactory, toolDAO);

    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("")
    @Operation(operationId = "getCategories", summary = "Retrieve all categories.", description = "Retrieve all categories.")
    @ApiResponse(responseCode = HttpStatus.SC_OK + "", description = "Successfully retrieved categories", content = @Content(mediaType = MediaType.APPLICATION_JSON, array = @ArraySchema(schema = @Schema(implementation = Category.class))))
    public List<Category> getCategories() {
        List<Category> categories = categoryDAO.getCategories();
        collectionHelper.evictAndSummarize(categories);
        return categories;
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/name/{name}")
    @Operation(operationId = "getCategoryByName", summary = "Retrieve a category by name.", description = "Retrieve a category by name.")
    @ApiResponse(responseCode = HttpStatus.SC_OK + "", description = "Successfully retrieved category", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = Category.class)))
    @ApiResponse(responseCode = HttpStatus.SC_NOT_FOUND + "", description = "Category not found")
    public Category getCategoryByName(@Parameter(description = "Category name.", name = "name", in = ParameterIn.PATH, required = true) @PathParam("name") String name) {
        Category category = categoryDAO.findByName(name);
        collectionHelper.throwExceptionForNullCollection(category);
        Hibernate.initialize(category.getAliases());
        collectionHelper.evictAndAddEntries(category);
        return (category);
    }
}
