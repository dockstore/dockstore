/*
 *    Copyright 2016 OICR
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
import java.util.EnumSet;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dockstore.webservice.core.Group;
import io.dockstore.webservice.core.Label;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.WorkflowVersion;
import io.dockstore.webservice.jdbi.FileDAO;
import io.dockstore.webservice.jdbi.GroupDAO;
import io.dockstore.webservice.jdbi.LabelDAO;
import io.dockstore.webservice.jdbi.TagDAO;
import io.dockstore.webservice.jdbi.TokenDAO;
import io.dockstore.webservice.jdbi.ToolDAO;
import io.dockstore.webservice.jdbi.UserDAO;
import io.dockstore.webservice.jdbi.WorkflowDAO;
import io.dockstore.webservice.jdbi.WorkflowVersionDAO;
import io.dockstore.webservice.resources.BitbucketOrgAuthenticationResource;
import io.dockstore.webservice.resources.DockerRepoResource;
import io.dockstore.webservice.resources.DockerRepoTagResource;
import io.dockstore.webservice.resources.GitHubComAuthenticationResource;
import io.dockstore.webservice.resources.GitHubRepoResource;
import io.dockstore.webservice.resources.GitLabComAuthenticationResource;
import io.dockstore.webservice.resources.QuayIOAuthenticationResource;
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
import io.dropwizard.hibernate.HibernateBundle;
import io.dropwizard.hibernate.UnitOfWorkAwareProxyFactory;
import io.dropwizard.migrations.MigrationsBundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.dropwizard.views.ViewBundle;
import io.swagger.api.MetadataApi;
import io.swagger.api.ToolClassesApi;
import io.swagger.api.ToolsApi;
import io.swagger.api.impl.ToolsApiServiceImpl;
import io.swagger.jaxrs.config.BeanConfig;
import io.swagger.jaxrs.listing.ApiListingResource;
import io.swagger.jaxrs.listing.SwaggerSerializers;
import okhttp3.Cache;
import okhttp3.OkHttpClient;
import okhttp3.OkUrlFactory;
import org.apache.http.client.HttpClient;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature;
import org.hibernate.Session;
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
    public static final String GA4GH_API_PATH = "/api/ga4gh/v1";
    private static final Logger LOG = LoggerFactory.getLogger(DockstoreWebserviceApplication.class);
    private static final int BYTES_IN_KILOBYTE = 1024;
    private static final int KILOBYTES_IN_MEGABYTE = 1024;
    private static final int CACHE_IN_MB = 100;
    private static Cache cache = null;

    private final HibernateBundle<DockstoreWebserviceConfiguration> hibernate = new HibernateBundle<DockstoreWebserviceConfiguration>(
            Token.class, Tool.class, User.class, Group.class, Tag.class, Label.class, SourceFile.class, Workflow.class,
            WorkflowVersion.class) {
        @Override
        public DataSourceFactory getDataSourceFactory(DockstoreWebserviceConfiguration configuration) {
            return configuration.getDataSourceFactory();
        }
    };

    public static void main(String[] args) throws Exception {
        new DockstoreWebserviceApplication().run(args);
    }

    @Override
    public String getName() {
        return "webservice";
    }

    @Override
    public void initialize(Bootstrap<DockstoreWebserviceConfiguration> bootstrap) {

        // setup hibernate+postgres
        bootstrap.addBundle(hibernate);

        // serve static html as well
        bootstrap.addBundle(new AssetsBundle("/assets/", "/static/"));
        // enable views
        bootstrap.addBundle(new ViewBundle<>());

        // for database migrations.xml
        bootstrap.addBundle(new MigrationsBundle<DockstoreWebserviceConfiguration>() {
            @Override
            public DataSourceFactory getDataSourceFactory(DockstoreWebserviceConfiguration configuration) {
                return configuration.getDataSourceFactory();
            }
        });

        if (cache == null) {
            int cacheSize = CACHE_IN_MB * BYTES_IN_KILOBYTE * KILOBYTES_IN_MEGABYTE; // 100 MiB
            final File tempDir;
            try {
                tempDir = Files.createTempDirectory("dockstore-web-cache-").toFile();
            } catch (IOException e) {
                LOG.error("Could no create web cache");
                throw new RuntimeException(e);
            }
            cache = new Cache(tempDir, cacheSize);
        }
        // match HttpURLConnection which does not have a timeout by default
        OkHttpClient okHttpClient = new OkHttpClient().newBuilder().cache(cache).connectTimeout(0, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS).writeTimeout(0, TimeUnit.SECONDS).build();
        try {
            // this can only be called once per JVM, a factory exception is thrown in our tests
            URL.setURLStreamHandlerFactory(new OkUrlFactory(okHttpClient));
        } catch (Error factoryException) {
            if (factoryException.getMessage().contains("factory already defined")) {
                LOG.info("OkHttpClient already registered, skipping");
            } else {
                LOG.error("Could no create web cache, factory exception");
                throw new RuntimeException(factoryException);
            }
        }
    }

    @Override
    public void run(DockstoreWebserviceConfiguration configuration, Environment environment) {
        BeanConfig beanConfig = new BeanConfig();
        beanConfig.setSchemes(new String[] { configuration.getScheme() });
        beanConfig.setHost(configuration.getHostname() + ':' + configuration.getPort());
        beanConfig.setBasePath("/");
        beanConfig.setResourcePackage("io.dockstore.webservice.resources,io.swagger.api");
        beanConfig.setScan(true);

        final QuayIOAuthenticationResource resource2 = new QuayIOAuthenticationResource(configuration.getQuayClientID(),
                configuration.getQuayRedirectURI());
        environment.jersey().register(resource2);

        final TemplateHealthCheck healthCheck = new TemplateHealthCheck(configuration.getTemplate());
        environment.healthChecks().register("template", healthCheck);

        final UserDAO userDAO = new UserDAO(hibernate.getSessionFactory());
        final TokenDAO tokenDAO = new TokenDAO(hibernate.getSessionFactory());
        final ToolDAO toolDAO = new ToolDAO(hibernate.getSessionFactory());
        final WorkflowDAO workflowDAO = new WorkflowDAO(hibernate.getSessionFactory());
        final WorkflowVersionDAO workflowVersionDAO = new WorkflowVersionDAO(hibernate.getSessionFactory());

        final GroupDAO groupDAO = new GroupDAO(hibernate.getSessionFactory());
        final TagDAO tagDAO = new TagDAO(hibernate.getSessionFactory());
        final LabelDAO labelDAO = new LabelDAO(hibernate.getSessionFactory());
        final FileDAO fileDAO = new FileDAO(hibernate.getSessionFactory());

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

        final ObjectMapper mapper = environment.getObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        final HttpClient httpClient = new HttpClientBuilder(environment).using(configuration.getHttpClientConfiguration()).build(getName());
        final DockerRepoResource dockerRepoResource = new DockerRepoResource(mapper, httpClient, userDAO, tokenDAO, toolDAO, tagDAO,
                labelDAO, fileDAO, configuration.getBitbucketClientID(), configuration.getBitbucketClientSecret());
        environment.jersey().register(dockerRepoResource);
        environment.jersey().register(new GitHubRepoResource(tokenDAO));
        environment.jersey().register(new DockerRepoTagResource(toolDAO, tagDAO));

        final GitHubComAuthenticationResource resource3 = new GitHubComAuthenticationResource(configuration.getGithubClientID(),
                configuration.getGithubRedirectURI());
        environment.jersey().register(resource3);

        final BitbucketOrgAuthenticationResource resource4 = new BitbucketOrgAuthenticationResource(configuration.getBitbucketClientID());
        environment.jersey().register(resource4);

        final GitLabComAuthenticationResource resource5 = new GitLabComAuthenticationResource(configuration.getGitlabClientID(),
                configuration.getGitlabRedirectURI());
        environment.jersey().register(resource5);

        environment.jersey().register(
                new TokenResource(tokenDAO, userDAO, configuration.getGithubClientID(), configuration.getGithubClientSecret(),
                        configuration.getBitbucketClientID(), configuration.getBitbucketClientSecret(), configuration.getGitlabClientID(),
                        configuration.getGitlabClientSecret(), configuration.getGitlabRedirectURI(), httpClient, cachingAuthenticator));

        final WorkflowResource workflowResource = new WorkflowResource(httpClient, userDAO, tokenDAO, toolDAO, workflowDAO,
                workflowVersionDAO, labelDAO, fileDAO, configuration.getBitbucketClientID(), configuration.getBitbucketClientSecret());
        environment.jersey().register(workflowResource);

        environment.jersey().register(new UserResource(httpClient, tokenDAO, userDAO, groupDAO, workflowResource, dockerRepoResource));

        // attach the container dao statically to avoid too much modification of generated code
        ToolsApiServiceImpl.setToolDAO(toolDAO);
        ToolsApiServiceImpl.setWorkflowDAO(workflowDAO);
        ToolsApiServiceImpl.setConfig(configuration);

        ToolsApiExtendedServiceImpl.setToolDAO(toolDAO);
        ToolsApiExtendedServiceImpl.setWorkflowDAO(workflowDAO);
        ToolsApiExtendedServiceImpl.setConfig(configuration);

        environment.jersey().register(new ToolsApi());
        environment.jersey().register(new ToolsExtendedApi());
        environment.jersey().register(new MetadataApi());
        environment.jersey().register(new ToolClassesApi());

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

        filterHolder.setInitParameter(ACCESS_CONTROL_ALLOW_METHODS_HEADER, "GET,POST,DELETE,PUT,OPTIONS");
        filterHolder.setInitParameter(ALLOWED_ORIGINS_PARAM, "*");
        filterHolder.setInitParameter(ALLOWED_METHODS_PARAM, "GET,POST,DELETE,PUT,OPTIONS");
        filterHolder.setInitParameter(ALLOWED_HEADERS_PARAM,
                "Authorization, X-Auth-Username, X-Auth-Password, X-Requested-With,Content-Type,Accept,Origin,Access-Control-Request-Headers,cache-control");

        // Add URL mapping
        // cors.addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), true, "/*");
        // cors.addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), false, environment.getApplicationContext().getContextPath() +
        // "*");

        /**
         * Ugly, but it does not look like there is a JPA standard annotation for partial indexes
         */
        Session session = hibernate.getSessionFactory().openSession();
        session.createSQLQuery(
                "CREATE UNIQUE INDEX IF NOT EXISTS full_workflow_name ON workflow (organization, repository, workflowname) WHERE workflowname IS NOT NULL;")
                .executeUpdate();
        session.createSQLQuery(
                "CREATE UNIQUE INDEX IF NOT EXISTS partial_workflow_name ON workflow (organization, repository) WHERE workflowname IS NULL;")
                .executeUpdate();
        session.createSQLQuery(
                "CREATE UNIQUE INDEX IF NOT EXISTS full_tool_name ON tool (registry, namespace, name, toolname) WHERE toolname IS NOT NULL")
                .executeUpdate();
        session.createSQLQuery("CREATE UNIQUE INDEX IF NOT EXISTS partial_tool_name ON tool (registry, namespace, name) WHERE toolname IS NULL;")
                .executeUpdate();
        session.close();
    }

    public HibernateBundle<DockstoreWebserviceConfiguration> getHibernate() {
        return hibernate;
    }
}
