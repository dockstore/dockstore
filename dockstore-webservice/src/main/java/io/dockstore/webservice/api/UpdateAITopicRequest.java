/*
 * Copyright 2024 OICR and UCSC
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

package io.dockstore.webservice.api;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.constraints.NotNull;

@Schema(description = "The AI topic update request")
public class UpdateAITopicRequest {

    @NotNull
    @Schema(description = "The AI topic", requiredMode = RequiredMode.REQUIRED)
    private String aiTopic;

    public UpdateAITopicRequest() {

    }

    public String getAiTopic() {
        return aiTopic;
    }

    public void setAiTopic(String aiTopic) {
        this.aiTopic = aiTopic;
    }
}
