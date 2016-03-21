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

import java.util.EnumSet;

import org.apache.http.client.HttpClient;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import io.dockstore.webservice.resources.QuayIOAuthenticationResource;
import io.dockstore.webservice.resources.TemplateHealthCheck;
import io.dockstore.webservice.resources.TokenResource;
import io.dockstore.webservice.resources.UserResource;
import io.dockstore.webservice.resources.WorkflowResource;
import io.dropwizard.Application;
import io.dropwizard.assets.AssetsBundle;
import io.dropwizard.auth.AuthFactory;
import io.dropwizard.auth.CachingAuthenticator;
import io.dropwizard.auth.oauth.OAuthFactory;
import io.dropwizard.client.HttpClientBuilder;
import io.dropwizard.db.DataSourceFactory;
import io.dropwizard.hibernate.HibernateBundle;
import io.dropwizard.migrations.MigrationsBundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.dropwizard.views.ViewBundle;
import io.swagger.api.ToolsApi;
import io.swagger.api.impl.ToolsApiServiceImpl;
import io.swagger.jaxrs.config.BeanConfig;
import io.swagger.jaxrs.listing.ApiListingResource;
import io.swagger.jaxrs.listing.SwaggerSerializers;

import static javax.servlet.DispatcherType.REQUEST;
import static org.eclipse.jetty.servlets.CrossOriginFilter.ACCESS_CONTROL_ALLOW_METHODS_HEADER;
import static org.eclipse.jetty.servlets.CrossOriginFilter.ALLOWED_HEADERS_PARAM;
import static org.eclipse.jetty.servlets.CrossOriginFilter.ALLOWED_METHODS_PARAM;
import static org.eclipse.jetty.servlets.CrossOriginFilter.ALLOWED_ORIGINS_PARAM;

/**
 *
 * @author dyuen
 */
public class DockstoreWebserviceApplication extends Application<DockstoreWebserviceConfiguration> {

    private static final Logger LOG = LoggerFactory.getLogger(DockstoreWebserviceApplication.class);
    public static final String GA4GH_API_PATH = "/api/v1";

    public static void main(String[] args) throws Exception {
        new DockstoreWebserviceApplication().run(args);
    }

    private final HibernateBundle<DockstoreWebserviceConfiguration> hibernate = new HibernateBundle<DockstoreWebserviceConfiguration>(
            Token.class, Tool.class, User.class, Group.class, Tag.class, Label.class, SourceFile.class, Workflow.class, WorkflowVersion.class) {
        @Override
        public DataSourceFactory getDataSourceFactory(DockstoreWebserviceConfiguration configuration) {
            return configuration.getDataSourceFactory();
        }
    };

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

        LOG.info("This is our custom logger saying that we're about to load authenticators");
        // setup authentication
        SimpleAuthenticator authenticator = new SimpleAuthenticator(tokenDAO);
        CachingAuthenticator<String, Token> cachingAuthenticator = new CachingAuthenticator<>(environment.metrics(), authenticator,
                configuration.getAuthenticationCachePolicy());
        environment.jersey().register(AuthFactory.binder(new OAuthFactory<>(cachingAuthenticator, "SUPER SECRET STUFF", Token.class)));

        final ObjectMapper mapper = environment.getObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        final HttpClient httpClient = new HttpClientBuilder(environment).using(configuration.getHttpClientConfiguration()).build(getName());
        final DockerRepoResource dockerRepoResource = new DockerRepoResource(mapper, httpClient, userDAO, tokenDAO, toolDAO, tagDAO, labelDAO,
                                                                       fileDAO, configuration.getBitbucketClientID(),
                                                                       configuration.getBitbucketClientSecret());
        environment.jersey().register(dockerRepoResource);
        environment.jersey().register(new GitHubRepoResource(tokenDAO, userDAO));
        environment.jersey().register(new DockerRepoTagResource(userDAO, toolDAO, tagDAO));

        final GitHubComAuthenticationResource resource3 = new GitHubComAuthenticationResource(configuration.getGithubClientID(),
                configuration.getGithubRedirectURI());
        environment.jersey().register(resource3);

        final BitbucketOrgAuthenticationResource resource4 = new BitbucketOrgAuthenticationResource(configuration.getBitbucketClientID());
        environment.jersey().register(resource4);

        environment.jersey().register(
                new TokenResource(tokenDAO, userDAO, configuration.getGithubClientID(), configuration.getGithubClientSecret(),
                        configuration.getBitbucketClientID(), configuration.getBitbucketClientSecret(), httpClient, cachingAuthenticator));

        final WorkflowResource workflowResource = new WorkflowResource(httpClient, userDAO, tokenDAO, workflowDAO, workflowVersionDAO,
                labelDAO, fileDAO, configuration.getBitbucketClientID(), configuration.getBitbucketClientSecret());
        environment.jersey().register(workflowResource);

        environment.jersey().register(new UserResource(httpClient, tokenDAO, userDAO, groupDAO, workflowResource, dockerRepoResource));


        // attach the container dao statically to avoid too much modification of generated code
        ToolsApiServiceImpl.setToolDAO(toolDAO);
        ToolsApiServiceImpl.setConfig(configuration);
        environment.jersey().register(new ToolsApi());

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
        filterHolder
                .setInitParameter(ALLOWED_HEADERS_PARAM,
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
