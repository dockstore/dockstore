/*
 *    Copyright 2018 OICR
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

package io.dockstore.webservice.resources;

import javax.annotation.Generated;
import javax.ws.rs.ext.Provider;

import io.swagger.annotations.Api;
import io.swagger.annotations.Contact;
import io.swagger.annotations.ExternalDocs;
import io.swagger.annotations.Info;
import io.swagger.annotations.License;
import io.swagger.annotations.SecurityDefinition;
import io.swagger.annotations.SwaggerDefinition;
import io.swagger.annotations.Tag;
import io.swagger.jaxrs.Reader;
import io.swagger.jaxrs.config.ReaderListener;
import io.swagger.models.Swagger;
import io.swagger.models.auth.ApiKeyAuthDefinition;
import io.swagger.models.auth.In;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.servers.Server;

import static io.dockstore.webservice.resources.ResourceConstants.JWT_SECURITY_DEFINITION_NAME;

/**
 * This is a dummy class used to describe the swagger API as a whole.
 * WARNING: This is a generated file, do not edit directly.
 * Edit the template in src/main/templates/io/dockstore/webservice/resources
 *
 * @author dyuen
 */
@Generated("maven resources template")
@Api("tools")
@Provider
//TODO clean this up, preferably by destroying swagger
@OpenAPIDefinition(tags = {@io.swagger.v3.oas.annotations.tags.Tag (name = "NIHdatacommons", description = ResourceConstants.NIHDATACOMMONS)}, servers = {@Server(url = "/api", description = "Current server when hosted on AWS"), @Server(url = "/", description = "When working locally"), @Server(url = "https://dockstore.org/api", description = "Production server"), @Server(url = "https://staging.dockstore.org/api", description = "Staging server"), @Server(url = "https://dev.dockstore.net/api", description = "Nightly build server")},
    info = @io.swagger.v3.oas.annotations.info.Info(description = Description.DESCRIPTION, version = "${project.version}", title = "Dockstore API", contact = @io.swagger.v3.oas.annotations.info.Contact(name = Description.NAME, email = Description.EMAIL, url = Description.CONTACT_URL), license = @io.swagger.v3.oas.annotations.info.License(name = Description.APACHE_LICENSE_VERSION_2_0, url = Description.LICENSE_LOCATION), termsOfService = Description.TOS_LOCATION)
)
@SwaggerDefinition(info = @Info(description =
    Description.DESCRIPTION, version = "${project.version}", title = "Dockstore API", contact = @Contact(name = Description.NAME, email = Description.EMAIL, url = Description.CONTACT_URL), license = @License(name = Description.APACHE_LICENSE_VERSION_2_0, url = Description.LICENSE_LOCATION), termsOfService = "TBD"), tags = {
    @Tag(name = "entries", description = ResourceConstants.ENTRIES),
    @Tag(name = "containers", description = ResourceConstants.CONTAINERS),
    @Tag(name = "aliases", description = ResourceConstants.ALIASES),
    @Tag(name = "containertags", description = ResourceConstants.CONTAINERTAGS),
    @Tag(name = "GA4GHV1", description = ResourceConstants.GA4GHV1),
    @Tag(name = "GA4GH", description = ResourceConstants.GA4GH),
    @Tag(name = "GA4GHV20", description = ResourceConstants.GA4GHV20),
    @Tag(name = "extendedGA4GH", description = ResourceConstants.EXTENDEDGA4GH),
    @Tag(name = "tokens", description = ResourceConstants.TOKENS),
    @Tag(name = "workflows", description = ResourceConstants.WORKFLOWS),
    @Tag(name = "organizations", description = ResourceConstants.ORGANIZATIONS),
    @Tag(name = "toolTester", description = ResourceConstants.TOOLTESTER),
    @Tag(name = "curation", description = ResourceConstants.CURATION),
    @Tag(name = "NIHdatacommons", description = ResourceConstants.NIHDATACOMMONS),
    @Tag(name = "hosted", description = ResourceConstants.HOSTED),
    @Tag(name = "users", description = ResourceConstants.USERS),
    @Tag(name = "metadata", description = ResourceConstants.METADATA) }, externalDocs = @ExternalDocs(value = "Dockstore documentation", url = "https://www.dockstore.org/docs/getting-started"), securityDefinition = @SecurityDefinition(apiKeyAuthDefinitions = {
    @io.swagger.annotations.ApiKeyAuthDefinition(key = JWT_SECURITY_DEFINITION_NAME, in = io.swagger.annotations.ApiKeyAuthDefinition.ApiKeyLocation.HEADER, name = "Authorization") }))
public class Description implements ReaderListener {

    public static final String DESCRIPTION = "This describes the dockstore API, a webservice that manages pairs of Docker images and associated metadata such as CWL documents and Dockerfiles used to build those images. Explore swagger.json for a Swagger 2.0 description of our API and explore openapi.yaml for OpenAPI 3.0 descriptions.";
    public static final String NAME = "Dockstore@ga4gh";
    public static final String EMAIL = "theglobalalliance@genomicsandhealth.org";
    public static final String CONTACT_URL = "https://discuss.dockstore.org/t/opening-helpdesk-tickets/1506";
    public static final String APACHE_LICENSE_VERSION_2_0 = "Apache License Version 2.0";
    public static final String LICENSE_LOCATION = "https://github.com/dockstore/dockstore/blob/develop/LICENSE";
    public static final String TOS_LOCATION = "https://github.com/dockstore/dockstore-ui2/raw/develop/src/assets/docs/Dockstore_Terms_of_Service.pdf";

    @Override
    public void beforeScan(Reader reader, Swagger swagger) {

    }

    @Override
    public void afterScan(Reader reader, Swagger swagger) {
        swagger.addSecurityDefinition(JWT_SECURITY_DEFINITION_NAME, new ApiKeyAuthDefinition("Authorization", In.HEADER));
    }
}
