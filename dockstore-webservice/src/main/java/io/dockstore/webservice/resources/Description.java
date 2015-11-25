package io.dockstore.webservice.resources;

import io.swagger.annotations.Contact;
import io.swagger.annotations.ExternalDocs;
import io.swagger.annotations.Info;
import io.swagger.annotations.License;
import io.swagger.annotations.SwaggerDefinition;
import io.swagger.annotations.Tag;

/**
 * @author dyuen
 */
@SwaggerDefinition(
        info = @Info(
                description = "This describes the dockstore API, a webservice that manages pairs of Docker images and associated metadata such as "
                        + "CWL documents and Dockerfiles used to build those images",
                version = "1.0.2",
                title = "Dockstore API",
                contact = @Contact(
                        name = "Dockstore@ga4gh",
                        email = " theglobalalliance@genomicsandhealth.org",
                        url = "https://github.com/ga4gh/dockstore"
                ),
                license = @License(
                        name = " GNU Lesser General Public License",
                        url = "https://www.gnu.org/licenses/lgpl-3.0.en.html"
                )
        ),
        consumes = {"application/json"},
        produces = {"application/json"},
        tags = {
                @Tag(name = "containers", description = "List and register entries in the dockstore (pairs of images + metadata (CWL and Dockerfile))"),
                @Tag(name = "GA4GH", description = "A curated subset of resources proposed as a common standard for tool repositories"),
                @Tag(name = "github.repo", description = "List source code repositories (should be generalized from github)"),
                @Tag(name = "integration.bitbucket.org", description = "stop-gap allowing developers to associate with bitbucket"),
                @Tag(name = "integration.github.com", description = "stop-gap allowing developers to associate with github"),
                @Tag(name = "integration.quay.io", description = "stop-gap allowing developers to associate with quay.io"),
                @Tag(name = "tokens", description = "List, modify, refresh, and delete tokens for external services"),
                @Tag(name = "users", description = "List, modify, and manage end users of the dockstore")
        },
        externalDocs = @ExternalDocs(value = "Dockstore documentation", url = "https://www.dockstore.org/docs/getting-started")
)
public class Description {
}
