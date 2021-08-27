package io.dockstore.webservice.resources;

import com.codahale.metrics.annotation.Timed;
import io.dockstore.webservice.CustomWebApplicationException;
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
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import org.apache.http.HttpStatus;
import org.hibernate.Hibernate;
import org.hibernate.SessionFactory;

/**
 * Category endpoints
 */
@Path("/categories")
@Api("/categories")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "categories", description = ResourceConstants.CATEGORIES)
@SecuritySchemes({ @SecurityScheme(type = SecuritySchemeType.HTTP, name = "bearer", scheme = "bearer") })
public class CategoryResource extends CollectionResource {

    private final SessionFactory sessionFactory;
    private final CategoryDAO categoryDAO;
    private final CollectionDAO collectionDAO;

    public CategoryResource(SessionFactory sessionFactory) {
        super(sessionFactory);
        this.sessionFactory = sessionFactory;
        this.categoryDAO = new CategoryDAO(sessionFactory);
        this.collectionDAO = new CollectionDAO(sessionFactory);
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/names")
    @ApiOperation(nickname = "getCategoryNames", value = "Retrieve all categories by name.", response = String.class, responseContainer = "List")
    @Operation(operationId = "getCategoryNames", summary = "Retrieve all categories by name.", description = "Retrieve all categories by name, sorted by category containing most entries first.")
    public List<String> getCategoryNames(@ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth Optional<User> user) {
        return categoryDAO.getCategoryNames();
    }

    @GET
    @Timed
    @UnitOfWork(readOnly = true)
    @Path("/names/{name}")
    @ApiOperation(nickname = "getCategoryByName", value = "TODO", response = Collection.class)
    @Operation(operationId = "getCategoryByName", summary = "Retrieve a category by name.", description = "Retrieve a category by name.")
    public Collection getCategoryByName(
        @ApiParam(hidden = true) @Parameter(hidden = true, name = "user") @Auth Optional<User> user,
        @ApiParam(value = "Category name.", required = true) @Parameter(description = "Category name.", name = "name", in = ParameterIn.PATH, required = true) @PathParam("name") String name) {
        Long specialId = categoryDAO.getSpecialOrganizationId();
        if (specialId == null) {
            String msg = "Category not found.";
            LOG.info(msg);
            throw new CustomWebApplicationException(msg, HttpStatus.SC_NOT_FOUND);
        }
        Collection collection = collectionDAO.findByNameAndOrg(name, specialId);
        throwExceptionForNullCollection(collection);
        Hibernate.initialize(collection.getAliases());
        addCollectionEntriesToCollection(collection);
        return collection;
    }
}
