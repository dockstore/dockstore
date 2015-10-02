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
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.Container;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.core.Group;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.jdbi.ContainerDAO;
import io.dockstore.webservice.jdbi.TokenDAO;
import io.dockstore.webservice.jdbi.UserDAO;
import io.dockstore.webservice.jdbi.GroupDAO;
import io.dockstore.webservice.jdbi.TagDAO;
import io.dockstore.webservice.resources.DockerRepoResource;
import io.dockstore.webservice.resources.GitHubComAuthenticationResource;
import io.dockstore.webservice.resources.GitHubRepoResource;
import io.dockstore.webservice.resources.QuayIOAuthenticationResource;
import io.dockstore.webservice.resources.TemplateHealthCheck;
import io.dockstore.webservice.resources.TokenResource;
import io.dockstore.webservice.resources.UserResource;
import io.dropwizard.Application;
import io.dropwizard.assets.AssetsBundle;
import io.dropwizard.client.HttpClientBuilder;
import io.dropwizard.db.DataSourceFactory;
import io.dropwizard.hibernate.HibernateBundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.dropwizard.views.ViewBundle;
import io.swagger.jaxrs.config.BeanConfig;
import io.swagger.jaxrs.listing.ApiListingResource;
import io.swagger.jaxrs.listing.SwaggerSerializers;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.EnumSet;
import java.util.Enumeration;
import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;
import org.apache.http.client.HttpClient;
import org.eclipse.jetty.servlets.CrossOriginFilter;

/**
 *
 * @author dyuen
 */
public class DockstoreWebserviceApplication extends Application<DockstoreWebserviceConfiguration> {

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
        String ip = "localhost";
        try {
            NetworkInterface ni = NetworkInterface.getByName("eth0");
            Enumeration<InetAddress> inetAddresses = ni.getInetAddresses();

            while (inetAddresses.hasMoreElements()) {
                InetAddress ia = inetAddresses.nextElement();
                if (!ia.isLinkLocalAddress()) {
                    System.out.println("IP: " + ia.getHostAddress());
                    ip = ia.getHostAddress();
                }
            }
        } catch (SocketException ex) {
            System.out.println("SocketException: " + ex);
        }

        // setup swagger
        BeanConfig beanConfig = new BeanConfig();
        beanConfig.setVersion("1.0.2");
        beanConfig.setSchemes(new String[] { "http" });
        beanConfig.setHost(ip + ":8080");
        beanConfig.setBasePath("/");
        beanConfig.setResourcePackage("io.dockstore.webservice.resources");
        beanConfig.setScan(true);
        beanConfig.setTitle("Swagger Remote Registry Prototype");

        // setup hibernate+postgres
        bootstrap.addBundle(hibernate);

        // serve static html as well
        bootstrap.addBundle(new AssetsBundle("/assets/", "/static/"));
        // enable views
        bootstrap.addBundle(new ViewBundle<DockstoreWebserviceConfiguration>());
    }

    @Override
    public void run(DockstoreWebserviceConfiguration configuration, Environment environment) {

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

        final ObjectMapper mapper = environment.getObjectMapper();

        final HttpClient httpClient = new HttpClientBuilder(environment).using(configuration.getHttpClientConfiguration()).build(getName());
        environment.jersey().register(new DockerRepoResource(mapper, httpClient, userDAO, tokenDAO, containerDAO, tagDAO));
        environment.jersey().register(new GitHubRepoResource(httpClient, tokenDAO));

        final GitHubComAuthenticationResource resource3 = new GitHubComAuthenticationResource(configuration.getGithubClientID(),
                configuration.getGithubRedirectURI());
        environment.jersey().register(resource3);

        environment.jersey().register(
                new TokenResource(mapper, tokenDAO, userDAO, configuration.getGithubClientID(), configuration.getGithubClientSecret(),
                        httpClient));

        environment.jersey().register(
                new UserResource(httpClient, tokenDAO, userDAO, groupDAO, configuration.getGithubClientID(), configuration
                        .getGithubClientSecret()));

        // swagger stuff

        // Swagger providers
        environment.jersey().register(ApiListingResource.class);
        environment.jersey().register(SwaggerSerializers.class);

        // optional CORS support
        // Enable CORS headers
        final FilterRegistration.Dynamic cors = environment.servlets().addFilter("CORS", CrossOriginFilter.class);

        // Configure CORS parameters
        cors.setInitParameter("allowedOrigins", "*");
        cors.setInitParameter("allowedHeaders", "X-Requested-With,Content-Type,Accept,Origin");
        cors.setInitParameter("allowedMethods", "OPTIONS,GET,PUT,POST,DELETE,HEAD");

        // Add URL mapping
        cors.addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), true, "/*");
    }
}
