/*
 *    Copyright 2017 OICR
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
package io.dockstore.common;

import java.lang.reflect.Type;
import java.util.Date;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import io.dockstore.client.cli.SearchClient;
import io.swagger.client.model.ToolV1;
import io.swagger.client.model.Workflow;

/**
 * Created by dyuen on 07/06/17.
 */
public class ToolWorkflowDeserializer implements JsonDeserializer<SearchClient.ElasticSearchObject.HitsInternal> {

    @Override
    public SearchClient.ElasticSearchObject.HitsInternal deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        Gson gson = new GsonBuilder().registerTypeAdapter(Date.class, new DateDeserializer()).create();

        SearchClient.ElasticSearchObject.HitsInternal internalHit = gson.fromJson(json, SearchClient.ElasticSearchObject.HitsInternal.class);

        JsonObject jsonObject = json.getAsJsonObject();
        JsonElement jsonType = jsonObject.get("_type");
        String type = jsonType.getAsString();

        JsonElement sourceElement = jsonObject.get("_source");
        String sourceString = sourceElement.toString();

        if ("workflow".equals(type)) {
            internalHit.source = gson.fromJson(sourceString, Workflow.class);
        } else if ("tool".equals(type)) {
            internalHit.source = gson.fromJson(sourceString, ToolV1.class);
        }

        return internalHit;
    }


    public class DateDeserializer implements JsonDeserializer<Date> {
        public Date deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            return new Date(json.getAsJsonPrimitive().getAsLong());
        }
    }
}
