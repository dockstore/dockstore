/*
 *    Copyright 2016 OICR
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

package io.dockstore.webservice.helpers;

import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.Registry;
import io.dockstore.webservice.core.Token;

/**
 * Create image registries
 *
 * @author dyuen
 */
public class ImageRegistryFactory {

    private final HttpClient client;
    private final Token quayToken;
    private final ObjectMapper objectMapper;

    public ImageRegistryFactory(final HttpClient client, final ObjectMapper objectMapper, final Token quayToken) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.quayToken = quayToken;
    }

    public List<ImageRegistryInterface> getAllRegistries() {
        List<ImageRegistryInterface> interfaces = new ArrayList<>();
        for (Registry r : Registry.values()) {
            ImageRegistryInterface anInterface = createImageRegistry(r);
            if (anInterface != null) {
                interfaces.add(anInterface);
            }
        }
        return interfaces;
    }

    public ImageRegistryInterface createImageRegistry(Registry registry) {
        if (registry == Registry.QUAY_IO) {
            if (quayToken == null) {
                return null;
            }

            return new QuayImageRegistry(client, objectMapper, quayToken);
        } else if (registry == Registry.DOCKER_HUB) {
            return new DockerHubRegistry(client);
        } else {
            throw new CustomWebApplicationException("Sorry, we do not support " + registry + ".", HttpStatus.SC_UNSUPPORTED_MEDIA_TYPE);
        }
    }
}
