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
package io.dockstore.webservice.core;

import io.swagger.v3.oas.annotations.ExternalDocumentation;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;

@OpenAPIDefinition(
    info = @Info(
        title = "Dockstore",
        //FIXME: use the templating system with the current description
        version = "1.6.0-alpha.4-SNAPSHOT",
        description = "The Dockstore API, includes proprietary and GA4GH V1+V2 endpoints",
        license = @License(name = "Apache 2.0", url = "https://github.com/ga4gh/dockstore/blob/develop/LICENSE"),
        contact = @Contact(url = "https://discuss.dockstore.org/t/opening-helpdesk-tickets/1506", name = "Dockstore@ga4gh", email = "theglobalalliance@genomicsandhealth.org")
    ),
    tags = {
        @io.swagger.v3.oas.annotations.tags.Tag(name = "metadata", description = "description of the webservice itself")
    },
    externalDocs = @ExternalDocumentation(description = "User documentation for dockstore", url = "https://docs.dockstore.org/")
)
public class OpenAPIDescription {
}
