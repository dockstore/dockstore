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
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.dockstore.webservice.helpers.TimestampDeserializer;
import io.swagger.v3.oas.annotations.media.Schema;
import java.sql.Timestamp;

public class WebhookRelease {

    @JsonProperty("tag_name")
    @Schema(name = "tag_name", description = "Name of the tag associated with the release", requiredMode = Schema.RequiredMode.REQUIRED, example = "mytag")
    private String tagName;

    /**
     * In the JSON sent by GitHub, this value is a string, without milliseconds, e.g., 2024-07-23T21:30:12Z.
     *
     * The Java generated OpenAPI adds milliseconds when serializing to JSON, e.g., 2024-07-23T21:30:12.123Z. We use the generated Java
     * OpenAPI in dockstore-support to both resend events and to deserialize from S3, so we need to handle both.
     *
     * The default Jackson deserializer is com.fasterxml.jackson.databind.deser.std.DateDeserializers.TimestampDeserializer, which uses
     * a SimpleDateFormat, which doesn't support optional values.
     *
     * The @JsonDeserialize uses a custom deserializer that can read in either case; the @JsonFormat annotation always serializes
     * without the millis.
     */
    @JsonProperty("published_at")
    @JsonDeserialize(using = TimestampDeserializer.class)
    @JsonFormat(shape = Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    @Schema(type = "String", format = "date-time")
    private Timestamp publishedAt;

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
