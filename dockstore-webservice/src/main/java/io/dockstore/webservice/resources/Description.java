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

package io.dockstore.webservice.resources;

import io.swagger.annotations.Contact;
import io.swagger.annotations.ExternalDocs;
import io.swagger.annotations.Info;
import io.swagger.annotations.License;
import io.swagger.annotations.SwaggerDefinition;
import io.swagger.annotations.Tag;

/**
 * This is a dummy class used to describe the swagger API as a whole
 * 
 * @author dyuen
 */
@SwaggerDefinition(info = @Info(description = "This describes the dockstore API, a webservice that manages pairs of Docker images and associated metadata such as "
        + "CWL documents and Dockerfiles used to build those images", version = "1.0.2", title = "Dockstore API", contact = @Contact(name = "Dockstore@ga4gh", email = " theglobalalliance@genomicsandhealth.org", url = "https://github.com/ga4gh/dockstore"), license = @License(name = " GNU Lesser General Public License", url = "https://www.gnu.org/licenses/lgpl-3.0.en.html")), consumes = "application/json", produces = "application/json", tags = {
        @Tag(name = "containers", description = "List and register entries in the dockstore (pairs of images + metadata (CWL and Dockerfile))"),
        @Tag(name = "containertags", description = "List and modify tags for containers"),
        @Tag(name = "GA4GH", description = "A curated subset of resources proposed as a common standard for tool repositories"),
        @Tag(name = "github.repo", description = "List source code repositories (should be generalized from github)"),
        @Tag(name = "integration.bitbucket.org", description = "stop-gap allowing developers to associate with bitbucket"),
        @Tag(name = "integration.github.com", description = "stop-gap allowing developers to associate with github"),
        @Tag(name = "integration.quay.io", description = "stop-gap allowing developers to associate with quay.io"),
        @Tag(name = "tokens", description = "List, modify, refresh, and delete tokens for external services"),
        @Tag(name = "workflows", description = "List and register workflows in the dockstore (CWL or WDL)"),
        @Tag(name = "users", description = "List, modify, and manage end users of the dockstore") }, externalDocs = @ExternalDocs(value = "Dockstore documentation", url = "https://www.dockstore.org/docs/getting-started"))
public class Description {
}
