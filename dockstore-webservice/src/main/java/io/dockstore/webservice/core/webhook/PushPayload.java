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

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

/**
 * A subset of fields available in a GitHub webhook push payload. The fields in this class are fields that the webservice uses.
 * Add more fields as we need them. Fields are from https://docs.github.com/en/webhooks-and-events/webhooks/webhook-events-and-payloads#push
 */
@Schema(description = "A model for a GitHub webhook push event", allOf = Payload.class)
public class PushPayload extends Payload {
    @Schema(description = "The full git ref that was pushed", requiredMode = RequiredMode.REQUIRED, example = "refs/heads/master OR refs/tags/v1.0")
    private String ref;

    // The following description is directly quoted from the above github doc
    @Schema(description = "The SHA of the most recent commit on ref after the push", requiredMode = RequiredMode.NOT_REQUIRED, example = "6d96270004515a0486bb7f76196a72b40c55a47f")
    private String after;

    public PushPayload() {
    }

    public String getRef() {
        return ref;
    }

    public void setRef(String ref) {
        this.ref = ref;
    }

    public String getAfter() {
        return after;
    }

    public void setAfter(String after) {
        this.after = after;
    }

    @Override
    @Schema(requiredMode = RequiredMode.REQUIRED) // Override schema to make repository required for the push event
    public WebhookRepository getRepository() {
        return super.getRepository();
    }
}
