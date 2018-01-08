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

package io.dockstore.webservice.resources;

import javax.ws.rs.ext.Provider;

import io.swagger.annotations.Contact;
import io.swagger.annotations.ExternalDocs;
import io.swagger.annotations.Info;
import io.swagger.annotations.License;
import io.swagger.annotations.SwaggerDefinition;
import io.swagger.annotations.Tag;
import io.swagger.jaxrs.Reader;
import io.swagger.jaxrs.config.ReaderListener;
import io.swagger.models.Swagger;
import io.swagger.models.auth.ApiKeyAuthDefinition;
import io.swagger.models.auth.In;

import static io.dockstore.webservice.Constants.JWT_SECURITY_DEFINITION_NAME;

/**
 * This is a dummy class used to describe the swagger API as a whole
 *
 * @author dyuen
 */
@Provider
@SwaggerDefinition(info = @Info(description =
        "This describes the dockstore API, a webservice that manages pairs of Docker images and associated metadata such as "
                + "CWL documents and Dockerfiles used to build those images", version = "1.3.0", title = "Dockstore API", contact = @Contact(name = "Dockstore@ga4gh", email = "theglobalalliance@genomicsandhealth.org", url = "https://github.com/ga4gh/dockstore"), license = @License(name = " GNU Lesser General Public License", url = "https://www.gnu.org/licenses/lgpl-3.0.en.html")), consumes = "application/json", produces = "application/json", tags = {
        @Tag(name = "containers", description = "List and register entries in the dockstore (pairs of images + metadata (CWL and Dockerfile))"),
        @Tag(name = "containertags", description = "List and modify tags for containers"),
        @Tag(name = "GA4GHV1", description = "A curated subset of resources proposed as a common standard for tool repositories"),
        @Tag(name = "GA4GHV2", description = "A curated subset of resources proposed as a common standard for tool repositories"),
        @Tag(name = "extendedGA4GH", description = "Optional experimental extensions of the GA4GH API"),
        @Tag(name = "github.repo", description = "List source code repositories (should be generalized from github)"),
        @Tag(name = "integration.bitbucket.org", description = "stop-gap allowing developers to associate with bitbucket"),
        @Tag(name = "integration.github.com", description = "stop-gap allowing developers to associate with github"),
        @Tag(name = "integration.gitlab.com", description = "stop-gap allowing developers to associate with gitlab"),
        @Tag(name = "integration.quay.io", description = "stop-gap allowing developers to associate with quay.io"),
        @Tag(name = "tokens", description = "List, modify, refresh, and delete tokens for external services"),
        @Tag(name = "workflows", description = "List and register workflows in the dockstore (CWL or WDL)"),
        @Tag(name = "users", description = "List, modify, and manage end users of the dockstore") }, externalDocs = @ExternalDocs(value = "Dockstore documentation", url = "https://www.dockstore.org/docs/getting-started"))
public class Description implements ReaderListener {
    @Override
    public void beforeScan(Reader reader, Swagger swagger) {

    }

    @Override
    public void afterScan(Reader reader, Swagger swagger) {
        swagger.addSecurityDefinition(JWT_SECURITY_DEFINITION_NAME, new ApiKeyAuthDefinition("Authorization", In.HEADER));
    }
}
