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
package io.dockstore.client.cli;

import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import io.swagger.client.model.PublishRequest;
import io.swagger.client.model.StarRequest;
import io.swagger.client.model.VerifyRequest;

public final class SwaggerUtility {

    private SwaggerUtility() {

    }

    /**
     * These serialization/deserialization hacks should not be necessary.
     * Why does this version of codegen remove the setters?
     * @param bool
     * @return
     */
    public static PublishRequest createPublishRequest(Boolean bool) {
        Map<String, Object> publishRequest = new HashMap<>();
        publishRequest.put("publish", bool);
        Gson gson = new Gson();
        String s = gson.toJson(publishRequest);
        return gson.fromJson(s, PublishRequest.class);
    }

    public static VerifyRequest createVerifyRequest(Boolean bool, String verifiedSource) {
        Map<String, Object> verifyRequest = new HashMap<>();
        verifyRequest.put("verify", bool);
        verifyRequest.put("verifiedSource", verifiedSource);
        Gson gson = new Gson();
        String s = gson.toJson(verifyRequest);
        return gson.fromJson(s, VerifyRequest.class);
    }

    public static StarRequest createStarRequest(Boolean bool) {
        Map<String, Object> starRequest = new HashMap<>();
        starRequest.put("star", bool);
        Gson gson = new Gson();
        String s = gson.toJson(starRequest);
        return gson.fromJson(s, StarRequest.class);
    }

}
