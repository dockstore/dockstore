/*
 * Copyright (C) 2015 Consonance
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.dockstore.webservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.dockstore.webservice.core.Container;
import io.dockstore.webservice.core.Group;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.jdbi.ContainerDAO;
import io.dockstore.webservice.jdbi.GroupDAO;
import io.dockstore.webservice.jdbi.TagDAO;
import io.dockstore.webservice.jdbi.TokenDAO;
import io.dockstore.webservice.jdbi.UserDAO;
import io.dockstore.webservice.resources.BitbucketOrgAuthenticationResource;
import io.dockstore.webservice.resources.DockerRepoResource;
import io.dockstore.webservice.resources.GitHubComAuthenticationResource;
import io.dockstore.webservice.resources.GitHubRepoResource;
import io.dockstore.webservice.resources.QuayIOAuthenticationResource;
import io.dockstore.webservice.resources.TemplateHealthCheck;
import io.dockstore.webservice.resources.TokenResource;
import io.dockstore.webservice.resources.UserResource;
import io.dropwizard.Application;
import io.dropwizard.assets.AssetsBundle;
import io.dropwizard.auth.AuthFactory;
import io.dropwizard.auth.CachingAuthenticator;
import io.dropwizard.auth.oauth.OAuthFactory;
import io.dropwizard.client.HttpClientBuilder;
import io.dropwizard.db.DataSourceFactory;
import io.dropwizard.hibernate.HibernateBundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.dropwizard.views.ViewBundle;
import io.swagger.jaxrs.config.BeanConfig;
import io.swagger.jaxrs.listing.ApiListingResource;
import io.swagger.jaxrs.listing.SwaggerSerializers;
import java.util.EnumSet;
import static javax.servlet.DispatcherType.REQUEST;
import org.apache.http.client.HttpClient;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import static org.eclipse.jetty.servlets.CrossOriginFilter.ACCESS_CONTROL_ALLOW_METHODS_HEADER;
import static org.eclipse.jetty.servlets.CrossOriginFilter.ALLOWED_HEADERS_PARAM;
import static org.eclipse.jetty.servlets.CrossOriginFilter.ALLOWED_METHODS_PARAM;
import static org.eclipse.jetty.servlets.CrossOriginFilter.ALLOWED_ORIGINS_PARAM;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author dyuen
 */
public class DockstoreWebserviceApplication extends Application<DockstoreWebserviceConfiguration> {

    private static final Logger LOG = LoggerFactory.getLogger(DockstoreWebserviceApplication.class);

    public static void main(String[] args) throws Exception {
        new DockstoreWebserviceApplication().run(args);
    }

    private final HibernateBundle<DockstoreWebserviceConfiguration> hibernate = new HibernateBundle<DockstoreWebserviceConfiguration>(
            Token.class, Container.class, User.class, Group.class, Tag.class) {
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
        // String ip = "localhost";
        // try {
        // NetworkInterface ni = NetworkInterface.getByName("eth0");
        // Enumeration<InetAddress> inetAddresses = ni.getInetAddresses();
        //
        // while (inetAddresses.hasMoreElements()) {
        // InetAddress ia = inetAddresses.nextElement();
        // if (!ia.isLinkLocalAddress()) {
        // System.out.println("IP: " + ia.getHostAddress());
        // ip = ia.getHostAddress();
        // }
        // }
        // } catch (SocketException ex) {
        // System.out.println("SocketException: " + ex);
        // }

        // setup swagger
        // BeanConfig beanConfig = new BeanConfig();
        // beanConfig.setVersion("1.0.2");
        // beanConfig.setSchemes(new String[] { "http" });
        // beanConfig.setHost(ip + ":8080");
        // beanConfig.setBasePath("/");
        // beanConfig.setResourcePackage("io.dockstore.webservice.resources");
        // beanConfig.setScan(true);
        // beanConfig.setTitle("Swagger Remote Registry Prototype");

        // setup hibernate+postgres
        bootstrap.addBundle(hibernate);

        // serve static html as well
        bootstrap.addBundle(new AssetsBundle("/assets/", "/static/"));
        // enable views
        bootstrap.addBundle(new ViewBundle<DockstoreWebserviceConfiguration>());
    }

    @Override
    public void run(DockstoreWebserviceConfiguration configuration, Environment environment) {
        BeanConfig beanConfig = new BeanConfig();
        beanConfig.setVersion("1.0.2");
        beanConfig.setTitle("Dockstore API");
        beanConfig.setSchemes(new String[] { configuration.getScheme() });
        beanConfig.setHost(configuration.getHostname() + ":" + configuration.getPort());
        beanConfig.setBasePath("/");
        beanConfig.setResourcePackage("io.dockstore.webservice.resources");
        beanConfig.setScan(true);

        final QuayIOAuthenticationResource resource2 = new QuayIOAuthenticationResource(configuration.getQuayClientID(),
                configuration.getQuayRedirectURI());
        environment.jersey().register(resource2);

        final TemplateHealthCheck healthCheck = new TemplateHealthCheck(configuration.getTemplate());
        environment.healthChecks().register("template", healthCheck);

        final UserDAO userDAO = new UserDAO(hibernate.getSessionFactory());
        final TokenDAO tokenDAO = new TokenDAO(hibernate.getSessionFactory());
        final ContainerDAO containerDAO = new ContainerDAO(hibernate.getSessionFactory());
        final GroupDAO groupDAO = new GroupDAO(hibernate.getSessionFactory());
        final TagDAO tagDAO = new TagDAO(hibernate.getSessionFactory());

        LOG.info("This is our custom logger saying that we're about to load authenticators");
        // setup authentication
        SimpleAuthenticator authenticator = new SimpleAuthenticator(tokenDAO);
        CachingAuthenticator<String, Token> cachingAuthenticator = new CachingAuthenticator<>(environment.metrics(), authenticator,
                configuration.getAuthenticationCachePolicy());
        environment.jersey().register(AuthFactory.binder(new OAuthFactory<Token>(cachingAuthenticator, "SUPER SECRET STUFF", Token.class)));

        final ObjectMapper mapper = environment.getObjectMapper();

        final HttpClient httpClient = new HttpClientBuilder(environment).using(configuration.getHttpClientConfiguration()).build(getName());
        environment.jersey().register(new DockerRepoResource(mapper, httpClient, userDAO, tokenDAO, containerDAO, tagDAO));
        environment.jersey().register(new GitHubRepoResource(httpClient, tokenDAO, userDAO));

        final GitHubComAuthenticationResource resource3 = new GitHubComAuthenticationResource(configuration.getGithubClientID(),
                configuration.getGithubRedirectURI());
        environment.jersey().register(resource3);

        final BitbucketOrgAuthenticationResource resource4 = new BitbucketOrgAuthenticationResource(configuration.getBitbucketClientID());
        environment.jersey().register(resource4);

        environment.jersey().register(
                new TokenResource(mapper, tokenDAO, userDAO, configuration.getGithubClientID(), configuration.getGithubClientSecret(),
                        configuration.getBitbucketClientID(), configuration.getBitbucketClientSecret(), httpClient, cachingAuthenticator));

        environment.jersey().register(
                new UserResource(mapper, httpClient, tokenDAO, userDAO, groupDAO, containerDAO, tagDAO, configuration.getGithubClientID(),
                        configuration.getGithubClientSecret()));

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
}
