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

package io.dockstore.webservice.helpers;

import io.dockstore.common.Registry;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.Token;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.apache.http.HttpStatus;

/**
 * Create image registries
 *
 * @author dyuen
 */
public class ImageRegistryFactory {

    private final Token quayToken;

    public ImageRegistryFactory(final Token quayToken) {
        this.quayToken = quayToken;
    }

    public List<AbstractImageRegistry> getAllRegistries() {
        List<AbstractImageRegistry> interfaces = new ArrayList<>();
        for (Registry r : Registry.values()) {
            AbstractImageRegistry anInterface = createImageRegistry(r);
            if (anInterface != null) {
                interfaces.add(anInterface);
            }
        }
        return interfaces;
    }

    public AbstractImageRegistry createImageRegistry(Registry registry) {
        // Private only registries should not have a default docker command value
        boolean validRegistry = Stream.of(Registry.values()).anyMatch(r -> r.name().equals(registry.name()));
        if (registry == Registry.QUAY_IO) {
            if (quayToken == null) {
                return null;
            }
            return new QuayImageRegistry(quayToken);
        } else if (validRegistry) {
            return new ManualRegistry(registry);
        } else {
            throw new CustomWebApplicationException("Sorry, we do not support " + registry + ".", HttpStatus.SC_UNSUPPORTED_MEDIA_TYPE);
        }
    }
}
