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

import static com.fasterxml.jackson.annotation.JsonFormat.Shape;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.sql.Timestamp;

public class WebhookRelease {

    @JsonProperty("tag_name")
    @Schema(name = "tag_name", description = "Name of the tag associated with the release", requiredMode = Schema.RequiredMode.REQUIRED, example = "mytag")
    private String tagName;

    @JsonProperty("published_at")
    @JsonFormat(shape = Shape.STRING, pattern = "YYYY-MM-DD'T'HH:MM:ss.SSS'Z'")
    private Timestamp publishedAt;

    public WebhookRelease() {
    }

    public String getTagName() {
        return tagName;
    }

    public void setTagName(String tagName) {
        this.tagName = tagName;
    }

    public Timestamp getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Timestamp publishedAt) {
        this.publishedAt = publishedAt;
    }
}
