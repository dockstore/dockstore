/*
 *    Copyright 2017 OICR
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

package io.dockstore.webservice;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.EnumSet;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.hibernate5.Hibernate5Module;
import com.google.common.base.MoreObjects;
import io.dockstore.webservice.core.Collection;
import io.dockstore.webservice.core.CollectionOrganization;
import io.dockstore.webservice.core.Event;
import io.dockstore.webservice.core.FileFormat;
import io.dockstore.webservice.core.Label;
import io.dockstore.webservice.core.OpenAPIDescription;
import io.dockstore.webservice.core.Organization;
import io.dockstore.webservice.core.OrganizationUser;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.Validation;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.WorkflowVersion;
import io.dockstore.webservice.doi.DOIGeneratorFactory;
import io.dockstore.webservice.helpers.ElasticManager;
import io.dockstore.webservice.helpers.GoogleHelper;
import io.dockstore.webservice.helpers.PersistenceExceptionMapper;
import io.dockstore.webservice.helpers.TransactionExceptionMapper;
import io.dockstore.webservice.jdbi.TagDAO;
import io.dockstore.webservice.jdbi.TokenDAO;
import io.dockstore.webservice.jdbi.ToolDAO;
import io.dockstore.webservice.jdbi.UserDAO;
import io.dockstore.webservice.jdbi.WorkflowDAO;
import io.dockstore.webservice.permissions.PermissionsFactory;
import io.dockstore.webservice.permissions.PermissionsInterface;
import io.dockstore.webservice.resources.CollectionResource;
import io.dockstore.webservice.resources.DockerRepoResource;
import io.dockstore.webservice.resources.DockerRepoTagResource;
import io.dockstore.webservice.resources.ElasticSearchHealthCheck;
import io.dockstore.webservice.resources.EntryResource;
import io.dockstore.webservice.resources.HostedToolResource;
import io.dockstore.webservice.resources.HostedWorkflowResource;
import io.dockstore.webservice.resources.MetadataResource;
import io.dockstore.webservice.resources.OrganizationResource;
import io.dockstore.webservice.resources.TemplateHealthCheck;
import io.dockstore.webservice.resources.TokenResource;
import io.dockstore.webservice.resources.UserResource;
import io.dockstore.webservice.resources.WorkflowResource;
import io.dockstore.webservice.resources.proposedGA4GH.ToolsApiExtendedServiceImpl;
import io.dockstore.webservice.resources.proposedGA4GH.ToolsExtendedApi;
import io.dropwizard.Application;
import io.dropwizard.assets.AssetsBundle;
import io.dropwizard.auth.AuthDynamicFeature;
import io.dropwizard.auth.AuthValueFactoryProvider;
import io.dropwizard.auth.CachingAuthenticator;
import io.dropwizard.auth.oauth.OAuthCredentialAuthFilter;
import io.dropwizard.client.HttpClientBuilder;
import io.dropwizard.db.DataSourceFactory;
import io.dropwizard.forms.MultiPartBundle;
import io.dropwizard.hibernate.HibernateBundle;
import io.dropwizard.hibernate.UnitOfWorkAwareProxyFactory;
import io.dropwizard.jersey.jackson.JsonProcessingExceptionMapper;
import io.dropwizard.migrations.MigrationsBundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.swagger.api.MetadataApi;
import io.swagger.api.MetadataApiV1;
import io.swagger.api.ToolClassesApi;
import io.swagger.api.ToolClassesApiV1;
import io.swagger.api.ToolsApi;
import io.swagger.api.ToolsApiV1;
import io.swagger.api.impl.ToolsApiServiceImpl;
import io.swagger.jaxrs.config.BeanConfig;
import io.swagger.jaxrs.listing.ApiListingResource;
import io.swagger.jaxrs.listing.SwaggerSerializers;
import io.swagger.v3.jaxrs2.integration.resources.OpenApiResource;
import okhttp3.Cache;
import okhttp3.OkHttpClient;
import okhttp3.OkUrlFactory;
import org.apache.http.client.HttpClient;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.glassfish.jersey.CommonProperties;
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static javax.servlet.DispatcherType.REQUEST;
import static org.eclipse.jetty.servlets.CrossOriginFilter.ACCESS_CONTROL_ALLOW_METHODS_HEADER;
import static org.eclipse.jetty.servlets.CrossOriginFilter.ALLOWED_HEADERS_PARAM;
import static org.eclipse.jetty.servlets.CrossOriginFilter.ALLOWED_METHODS_PARAM;
import static org.eclipse.jetty.servlets.CrossOriginFilter.ALLOWED_ORIGINS_PARAM;

/**
 * @author dyuen
 */
public class DockstoreWebserviceApplication extends Application<DockstoreWebserviceConfiguration> {
    public static final String GA4GH_API_PATH = "/api/ga4gh/v2";
    public static final String GA4GH_API_PATH_V1 = "/api/ga4gh/v1";
    private static final Logger LOG = LoggerFactory.getLogger(DockstoreWebserviceApplication.class);
    private static final int BYTES_IN_KILOBYTE = 1024;
    private static final int KILOBYTES_IN_MEGABYTE = 1024;
    private static final int CACHE_IN_MB = 100;
    private static Cache cache = null;

    private final HibernateBundle<DockstoreWebserviceConfiguration> hibernate = new HibernateBundle<DockstoreWebserviceConfiguration>(
            Token.class, Tool.class, User.class, Tag.class, Label.class, SourceFile.class, Workflow.class, CollectionOrganization.class,
            WorkflowVersion.class, FileFormat.class, Organization.class, OrganizationUser.class, Event.class, Collection.class, Validation.class) {
        @Override
        public DataSourceFactory getDataSourceFactory(DockstoreWebserviceConfiguration configuration) {
            return configuration.getDataSourceFactory();
        }
    };

    public static void main(String[] args) throws Exception {
        new DockstoreWebserviceApplication().run(args);
    }

    public static Cache getCache() {
        return cache;
    }

    @Override
    public String getName() {
        return "webservice";
    }

    @Override
    public void initialize(Bootstrap<DockstoreWebserviceConfiguration> bootstrap) {

        configureMapper(bootstrap.getObjectMapper());


        // setup hibernate+postgres
        bootstrap.addBundle(hibernate);

        // serve static html as well
        bootstrap.addBundle(new AssetsBundle("/assets/", "/static/"));

        // for database migrations.xml
        bootstrap.addBundle(new MigrationsBundle<DockstoreWebserviceConfiguration>() {
            @Override
            public DataSourceFactory getDataSourceFactory(DockstoreWebserviceConfiguration configuration) {
                return configuration.getDataSourceFactory();
            }
        });

        bootstrap.addBundle(new MultiPartBundle());

        if (cache == null) {
            int cacheSize = CACHE_IN_MB * BYTES_IN_KILOBYTE * KILOBYTES_IN_MEGABYTE; // 100 MiB
            final File cacheDir;
            try {
                // let's try using the same cache each time
                // not sure how corruptible/non-curruptable the cache is
                // https://github.com/square/okhttp/blob/parent-3.10.0/okhttp/src/main/java/okhttp3/internal/cache/DiskLruCache.java#L82 looks promising
                cacheDir = Files.createDirectories(Paths.get("/tmp/dockstore-web-cache")).toFile();
            } catch (IOException e) {
                LOG.error("Could no create or re-use web cache");
                throw new RuntimeException(e);
            }
            cache = new Cache(cacheDir, cacheSize);
        }
        // match HttpURLConnection which does not have a timeout by default
        OkHttpClient okHttpClient = new OkHttpClient().newBuilder().cache(cache).connectTimeout(0, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS).writeTimeout(0, TimeUnit.SECONDS).build();
        try {
            // this can only be called once per JVM, a factory exception is thrown in our tests
            URL.setURLStreamHandlerFactory(new OkUrlFactory(okHttpClient));
        } catch (Error factoryException) {
            if (factoryException.getMessage().contains("factory already defined")) {
                LOG.debug("OkHttpClient already registered, skipping");
            } else {
                LOG.error("Could no create web cache, factory exception");
                throw new RuntimeException(factoryException);
            }
        }
    }

    private static void configureMapper(ObjectMapper objectMapper) {
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        objectMapper.registerModule(new Hibernate5Module());
        objectMapper.enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY);
        objectMapper.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        // doesn't seem to work, when it does, we could avoid overriding pojo.mustache in swagger
        objectMapper.enable(MapperFeature.ALLOW_EXPLICIT_PROPERTY_RENAMING);
    }

    @Override
    public void run(DockstoreWebserviceConfiguration configuration, Environment environment) {
        BeanConfig beanConfig = new BeanConfig();
        beanConfig.setSchemes(new String[] { configuration.getExternalConfig().getScheme() });
        String portFragment = configuration.getExternalConfig().getPort() == null ? "" : ":" + configuration.getExternalConfig().getPort();
        beanConfig.setHost(configuration.getExternalConfig().getHostname() + portFragment);
        beanConfig.setBasePath(MoreObjects.firstNonNull(configuration.getExternalConfig().getBasePath(), "/"));
        beanConfig.setResourcePackage("io.dockstore.webservice.resources,io.swagger.api");
        beanConfig.setScan(true);
        ElasticManager.setConfig(configuration);
        environment.jersey().property(CommonProperties.FEATURE_AUTO_DISCOVERY_DISABLE, true);
        environment.jersey().register(new JsonProcessingExceptionMapper(true));

        final TemplateHealthCheck healthCheck = new TemplateHealthCheck(configuration.getTemplate());
        environment.healthChecks().register("template", healthCheck);

        final ElasticSearchHealthCheck elasticSearchHealthCheck = new ElasticSearchHealthCheck(new ToolsExtendedApi());
        environment.healthChecks().register("elasticSearch", elasticSearchHealthCheck);

        final UserDAO userDAO = new UserDAO(hibernate.getSessionFactory());
        final TokenDAO tokenDAO = new TokenDAO(hibernate.getSessionFactory());
        final ToolDAO toolDAO = new ToolDAO(hibernate.getSessionFactory());
        final WorkflowDAO workflowDAO = new WorkflowDAO(hibernate.getSessionFactory());
        final TagDAO tagDAO = new TagDAO(hibernate.getSessionFactory());

        LOG.info("Cache directory for OkHttp is: " + cache.directory().getAbsolutePath());
        LOG.info("This is our custom logger saying that we're about to load authenticators");
        // setup authentication to allow session access in authenticators, see https://github.com/dropwizard/dropwizard/pull/1361
        SimpleAuthenticator authenticator = new UnitOfWorkAwareProxyFactory(getHibernate())
                .create(SimpleAuthenticator.class, new Class[] { TokenDAO.class, UserDAO.class }, new Object[] { tokenDAO, userDAO });
        CachingAuthenticator<String, User> cachingAuthenticator = new CachingAuthenticator<>(environment.metrics(), authenticator,
                configuration.getAuthenticationCachePolicy());
        environment.jersey().register(new AuthDynamicFeature(
                new OAuthCredentialAuthFilter.Builder<User>().setAuthenticator(cachingAuthenticator).setAuthorizer(new SimpleAuthorizer())
                        .setPrefix("Bearer").setRealm("SUPER SECRET STUFF").buildAuthFilter()));
        environment.jersey().register(new AuthValueFactoryProvider.Binder<>(User.class));
        environment.jersey().register(RolesAllowedDynamicFeature.class);

        final HttpClient httpClient = new HttpClientBuilder(environment).using(configuration.getHttpClientConfiguration()).build(getName());

        final PermissionsInterface authorizer = PermissionsFactory.getAuthorizer(tokenDAO, configuration);
        final WorkflowResource workflowResource = new WorkflowResource(httpClient, hibernate.getSessionFactory(), configuration.getBitbucketClientID(), configuration.getBitbucketClientSecret(), authorizer);
        environment.jersey().register(workflowResource);

        // Note workflow resource must be passed to the docker repo resource, as the workflow resource refresh must be called for checker workflows
        final DockerRepoResource dockerRepoResource = new DockerRepoResource(environment.getObjectMapper(), httpClient, hibernate.getSessionFactory(), configuration.getBitbucketClientID(), configuration.getBitbucketClientSecret(), workflowResource);
        environment.jersey().register(dockerRepoResource);
        environment.jersey().register(new DockerRepoTagResource(toolDAO, tagDAO));
        environment.jersey().register(new TokenResource(tokenDAO, userDAO, httpClient, cachingAuthenticator, configuration));

        environment.jersey().register(new UserResource(getHibernate().getSessionFactory(), workflowResource, dockerRepoResource, cachingAuthenticator, authorizer));
        environment.jersey().register(new MetadataResource(getHibernate().getSessionFactory(), configuration));
        environment.jersey().register(new HostedToolResource(getHibernate().getSessionFactory(), authorizer, configuration.getLimitConfig()));
        environment.jersey().register(new HostedWorkflowResource(getHibernate().getSessionFactory(), authorizer, configuration.getLimitConfig()));
        environment.jersey().register(new EntryResource(toolDAO));
        environment.jersey().register(new OrganizationResource(getHibernate().getSessionFactory()));
        environment.jersey().register(new CollectionResource(getHibernate().getSessionFactory()));
        environment.jersey().register(OpenApiResource.class);
        environment.jersey().register(OpenAPIDescription.class);


        // attach the container dao statically to avoid too much modification of generated code
        ToolsApiServiceImpl.setToolDAO(toolDAO);
        ToolsApiServiceImpl.setWorkflowDAO(workflowDAO);
        ToolsApiServiceImpl.setConfig(configuration);

        ToolsApiExtendedServiceImpl.setToolDAO(toolDAO);
        ToolsApiExtendedServiceImpl.setWorkflowDAO(workflowDAO);
        ToolsApiExtendedServiceImpl.setConfig(configuration);

        DOIGeneratorFactory.setConfig(configuration);

        GoogleHelper.setConfig(configuration);

        ToolsApi toolsApi = new ToolsApi(null);
        environment.jersey().register(toolsApi);
        environment.jersey().register(new ToolsExtendedApi());
        environment.jersey().register(new MetadataApi(null));
        environment.jersey().register(new ToolClassesApi(null));
        environment.jersey().register(new PersistenceExceptionMapper());
        environment.jersey().register(new TransactionExceptionMapper());

        environment.jersey().register(new ToolsApiV1());
        environment.jersey().register(new MetadataApiV1());
        environment.jersey().register(new ToolClassesApiV1());

        // extra renderers
        environment.jersey().register(new CharsetResponseFilter());

        // swagger stuff

        // Swagger providers
        environment.jersey().register(ApiListingResource.class);
        environment.jersey().register(SwaggerSerializers.class);

        // optional CORS support
        // Enable CORS headers
        // final FilterRegistration.Dynamic cors = environment.servlets().addFilter("CORS", CrossOriginFilter.class);
        final FilterHolder filterHolder = environment.getApplicationContext().addFilter(CrossOriginFilter.class, "/*", EnumSet.of(REQUEST));

        // Configure CORS parameters
        // cors.setInitParameter("allowedOrigins", "*");
        // cors.setInitParameter("allowedHeaders", "X-Requested-With,Content-Type,Accept,Origin");
        // cors.setInitParameter("allowedMethods", "OPTIONS,GET,PUT,POST,DELETE,HEAD");

        filterHolder.setInitParameter(ACCESS_CONTROL_ALLOW_METHODS_HEADER, "GET,POST,DELETE,PUT,OPTIONS,PATCH");
        filterHolder.setInitParameter(ALLOWED_ORIGINS_PARAM, "*");
        filterHolder.setInitParameter(ALLOWED_METHODS_PARAM, "GET,POST,DELETE,PUT,OPTIONS,PATCH");
        filterHolder.setInitParameter(ALLOWED_HEADERS_PARAM,
                "Authorization, X-Auth-Username, X-Auth-Password, X-Requested-With,Content-Type,Accept,Origin,Access-Control-Request-Headers,cache-control");

        // Add URL mapping
        // cors.addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), true, "/*");
        // cors.addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), false, environment.getApplicationContext().getContextPath() +
        // "*");

    }

    public HibernateBundle<DockstoreWebserviceConfiguration> getHibernate() {
        return hibernate;
    }
}
