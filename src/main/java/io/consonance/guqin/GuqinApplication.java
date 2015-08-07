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
package io.consonance.guqin;

import io.consonance.guqin.core.Token;
import io.consonance.guqin.jdbi.TokenDAO;
import io.consonance.guqin.resources.DockerRepoResource;
import io.consonance.guqin.resources.HelloWorldResource;
import io.consonance.guqin.resources.QuayIOAuthenticationResource;
import io.consonance.guqin.resources.TemplateHealthCheck;
import io.consonance.guqin.resources.TokenResource;
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
import java.util.EnumSet;
import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;
import org.apache.http.client.HttpClient;
import org.eclipse.jetty.servlets.CrossOriginFilter;

/**
 *
 * @author dyuen
 */
public class GuqinApplication extends Application<GuqinConfiguration> {

    public static void main(String[] args) throws Exception {
        new GuqinApplication().run(args);
    }

    private final HibernateBundle<GuqinConfiguration> hibernate = new HibernateBundle<GuqinConfiguration>(Token.class) {
        @Override
        public DataSourceFactory getDataSourceFactory(GuqinConfiguration configuration) {
            return configuration.getDataSourceFactory();
        }
    };

    @Override
    public String getName() {
        return "guqin";
    }

    @Override
    public void initialize(Bootstrap<GuqinConfiguration> bootstrap) {
        // setup swagger
        BeanConfig beanConfig = new BeanConfig();
        beanConfig.setVersion("1.0.2");
        beanConfig.setVersion("1.0.2");
        beanConfig.setSchemes(new String[] { "http" });
        beanConfig.setHost("localhost:8080");
        beanConfig.setBasePath("/api");
        beanConfig.setResourcePackage("io.consonance.guqin.resources");
        beanConfig.setScan(true);
        beanConfig.setTitle("Swagger Remote Registry Prototype");

        // setup hibernate+postgres
        bootstrap.addBundle(hibernate);

        // serve static html as well
        bootstrap.addBundle(new AssetsBundle("/assets/", "/static/"));
        // enable views
        bootstrap.addBundle(new ViewBundle<GuqinConfiguration>());
    }

    @Override
    public void run(GuqinConfiguration configuration, Environment environment) {

        final HelloWorldResource resource = new HelloWorldResource(configuration.getTemplate(), configuration.getDefaultName());
        environment.jersey().register(resource);
        final QuayIOAuthenticationResource resource2 = new QuayIOAuthenticationResource(configuration.getClientID(),
                configuration.getRedirectURI());
        environment.jersey().register(resource2);

        final TokenDAO dao = new TokenDAO(hibernate.getSessionFactory());
        environment.jersey().register(new TokenResource(dao));

        final TemplateHealthCheck healthCheck = new TemplateHealthCheck(configuration.getTemplate());
        environment.healthChecks().register("template", healthCheck);

        final HttpClient httpClient = new HttpClientBuilder(environment).using(configuration.getHttpClientConfiguration()).build(getName());
        environment.jersey().register(new DockerRepoResource(httpClient, dao));

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
