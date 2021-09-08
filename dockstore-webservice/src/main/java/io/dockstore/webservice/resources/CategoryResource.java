package io.dockstore.webservice.resources;

import com.codahale.metrics.annotation.Timed;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.Category;
import io.dockstore.webservice.core.Collection;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.jdbi.CategoryDAO;
import io.dockstore.webservice.jdbi.CollectionDAO;
import io.dropwizard.auth.Auth;
import io.dropwizard.hibernate.UnitOfWork;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.security.SecuritySchemes;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Optional;
// import java.util.stream.Collectors;
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
@Api("/categories")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "categories", description = ResourceConstants.CATEGORIES)
@SecuritySchemes({ @SecurityScheme(type = SecuritySchemeType.HTTP, name = "bearer", scheme = "bearer") })
public class CategoryResource implements AuthenticatedResourceInterface {

    private static final Logger LOG = LoggerFactory.getLogger(CategoryResource.class);

    private final SessionFactory sessionFactory;
    private final CollectionResource collectionResource;
    private final CategoryDAO categoryDAO;
    private final CollectionDAO collectionDAO;

    public CategoryResource(SessionFactory sessionFactory, CollectionResource collectionResource) {
        this.sessionFactory = sessionFactory;
        this.collectionResource = collectionResource;
        this.categoryDAO = new CategoryDAO(sessionFactory);
        this.collectionDAO = new CollectionDAO(sessionFactory);
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("")
    @ApiOperation(nickname = "getCategories", value = "Retrieve all categories.", response = Category.class, responseContainer = "List")
    @Operation(operationId = "getCategories", summary = "Retrieve all categories.", description = "Retrieve all categories.")
    public List<Category> getCategories(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth Optional<User> user) {
        return categoryDAO.getCategories();
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/names")
    @ApiOperation(nickname = "getCategoryNames", value = "Retrieve all categories by name.", response = String.class, responseContainer = "List")
    @Operation(operationId = "getCategoryNames", summary = "Retrieve all categories by name.", description = "Retrieve all categories by name, sorted by the category containing the most entries first.")
    public List<String> getCategoryNames(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth Optional<User> user) {
        return categoryDAO.getCategoryNames();
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/names/{name}")
    @ApiOperation(nickname = "getCategoryByName", value = "Retrieve a category by name.", response = Category.class)
    @Operation(operationId = "getCategoryByName", summary = "Retrieve a category by name.", description = "Retrieve a category by name.")
    public Category getCategoryByName(
        @ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth Optional<User> user,
        @ApiParam(value = "Category name.", required = true) @Parameter(description = "Category name.", name = "name", in = ParameterIn.PATH, required = true) @PathParam("name") String name) {
        Collection collection = collectionDAO.findByNameAndOrg(name, getSpecialId());
        collectionResource.throwExceptionForNullCollection(collection);
        // TODO make sure this is a category rather than regular collection.
        Hibernate.initialize(collection.getAliases());
        collectionResource.addCollectionEntriesToCollection(collection);
        return ((Category)collection);
    }

    private Long getSpecialId() {
        Long specialId = categoryDAO.getSpecialOrganizationId();
        if (specialId == null) {
            String msg = "Category not found.";
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_NOT_FOUND);
        }
        return specialId;
    }
}
