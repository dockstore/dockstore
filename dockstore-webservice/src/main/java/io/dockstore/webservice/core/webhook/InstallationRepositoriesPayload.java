/*
 * Copyright 2023 OICR and UCSC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *           http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.dockstore.webservice.core.webhook;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.util.List;

/**
 * A subset of fields available in a GitHub webhook installation_repositories payload. The fields in this class are fields that the webservice uses.
 * Add more fields as we need them. Fields are from https://docs.github.com/en/webhooks-and-events/webhooks/webhook-events-and-payloads#installation_repositories
 */
@Schema(description = "A model for a GitHub webhook installation event", allOf = Payload.class, example = InstallationRepositoriesPayload.EXAMPLE)
public class InstallationRepositoriesPayload extends Payload {
    // Example excluding the 'repository' property because it's not needed for this payload
    static final String EXAMPLE = """
                {
                  "sender": {
                    "login": "string"
                  },
                  "installation": {
                    "id": 0
                  },
                  "repositories_added": [
                    {
                      "full_name": "dockstore/dockstore-ui2"
                    }
                  ]
                }
                """;

    @JsonProperty("repositories_added")
    @Schema(name = "repositories_added", description = "An array of repository objects, which were added to the installation", requiredMode = RequiredMode.REQUIRED)
    private List<WebhookRepository> repositoriesAdded;

    public InstallationRepositoriesPayload() {
    }

    public List<WebhookRepository> getRepositoriesAdded() {
        return repositoriesAdded;
    }

    public void setRepositoriesAdded(List<WebhookRepository> repositoriesAdded) {
        this.repositoriesAdded = repositoriesAdded;
    }
}
