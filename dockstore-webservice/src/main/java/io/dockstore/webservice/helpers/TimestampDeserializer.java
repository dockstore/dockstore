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

package io.dockstore.webservice.helpers;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;

/**
 * Handles deserializing dates in string formats. In particular, can handle both strings with milliseconds and without milliseconds. See
 * @{see io.dockstore.core.webhook.WebhookRelease}
 */
public class TimestampDeserializer extends StdDeserializer<Timestamp> {

    public TimestampDeserializer() {
        this(null);
    }

    public TimestampDeserializer(Class<?> vc) {
        super(vc);
    }

    @Override
    public Timestamp deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JacksonException {
        final String dateStr = p.getText();
        if (dateStr == null) {
            return null;
        }
        final OffsetDateTime offsetDateTime = OffsetDateTime.parse(dateStr);
        return new Timestamp((offsetDateTime.toInstant().toEpochMilli()));
    }
}
