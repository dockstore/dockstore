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

import io.dockstore.webservice.core.Installation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

/**
 * A subset of fields available in a GitHub webhook payload. The fields in this class are fields that the webservice uses.
 * Add more fields as we need them. Fields are from https://docs.github.com/en/webhooks-and-events/webhooks/webhook-events-and-payloads#webhook-payload-object-common-properties
 */
@Schema(description = "Describes the common fields of all GitHub webhook payloads", subTypes = { PushPayload.class, InstallationRepositoriesPayload.class })
public abstract class Payload {

    @Schema(description = "The repository where the event occurred. Webhook payloads contain the repository property when the event occurs from activity in a repository")
    private WebhookRepository repository;

    @Schema(description = "The user that triggered the event", requiredMode = RequiredMode.REQUIRED)
    private Sender sender;

    @Schema(description = "The GitHub App installation", requiredMode = RequiredMode.REQUIRED)
    private Installation installation;

    protected Payload() {
    }

    public WebhookRepository getRepository() {
        return repository;
    }

    public void setRepository(WebhookRepository repository) {
        this.repository = repository;
    }

    public Sender getSender() {
        return sender;
    }

    public void setSender(Sender sender) {
        this.sender = sender;
    }

    public Installation getInstallation() {
        return installation;
    }

    public void setInstallation(Installation installation) {
        this.installation = installation;
    }
}
