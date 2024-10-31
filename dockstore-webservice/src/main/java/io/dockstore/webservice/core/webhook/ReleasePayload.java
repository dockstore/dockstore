/*
 * Copyright 2024 OICR and UCSC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.dockstore.webservice.core.webhook;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Arrays;
import java.util.Optional;

/**
 * A subset of the fields available in a GitHub webhook release payload. The fields in this class are fields that the webservice uses.
 *  * Add more fields as we need them. Fields are from https://docs.github.com/en/webhooks-and-events/webhooks/webhook-events-and-payloads#release
 */
public class ReleasePayload extends Payload {

    @JsonProperty
    @Schema(name = "release", description = "Details about the release", requiredMode = RequiredMode.REQUIRED)
    private WebhookRelease release;

    @JsonProperty
    @Schema(name = "action", description = "The action which the event describes", requiredMode = RequiredMode.REQUIRED)
    private String action;

    @JsonProperty
    @Schema(name = "draft", description = "Whether this is a draft release", requiredMode = RequiredMode.REQUIRED)
    private boolean draft;

    public boolean isDraft() {
        return draft;
    }

    public void setDraft(boolean draft) {
        this.draft = draft;
    }

    public WebhookRelease getRelease() {
        return release;
    }

    public void setRelease(WebhookRelease release) {
        this.release = release;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    /**
     * This enum represents the values of the release event's "action" property which we
     * currently expect to receive.  Potentially, GitHub could change the action names or add more actions,
     * so we don't use this enum as the type of the "action" field in ReleasePayload, because
     * we'd like to be able to process events with unknown action types, if for no other reason than to be
     * able to gracefully log and reject them.
     */
    public enum Action {
        CREATED("created"),
        DELETED("deleted"),
        EDITED("edited"),
        PRE_RELEASED("prereleased"),
        PUBLISHED("published"),
        RELEASED("released"),
        UNPUBLISHED("unpublished");

        private String value;


        Action(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return value;
        }

        public static Optional<Action> findAction(String actionString) {
            return Arrays.stream(Action.values()).filter(action -> action.toString().equals(actionString)).findFirst();
        }
    }
}
