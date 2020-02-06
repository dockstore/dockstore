/*
 *
 *  *    Copyright 2020 OICR
 *  *
 *  *    Licensed under the Apache License, Version 2.0 (the "License");
 *  *    you may not use this file except in compliance with the License.
 *  *    You may obtain a copy of the License at
 *  *
 *  *        http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  *    Unless required by applicable law or agreed to in writing, software
 *  *    distributed under the License is distributed on an "AS IS" BASIS,
 *  *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  *    See the License for the specific language governing permissions and
 *  *    limitations under the License.
 *
 */

package io.dockstore.common;

import java.util.Arrays;
import java.util.Optional;

public enum DescriptorLanguageSubclass {
    DOCKER_COMPOSE("Docker Compose", "docker-compose"),
    HELM("Helm", "helm");

    private final String friendlyName;

    private final String shortName;

    DescriptorLanguageSubclass(final String friendlyName, final String shortName) {
        this.friendlyName = friendlyName;
        this.shortName = shortName;
    }

    @Override
    public String toString() {
        return friendlyName;
    }

    public String getFriendlyName() {
        return friendlyName;
    }

    public String getShortName() {
        return shortName;
    }

    public static DescriptorLanguageSubclass convertShortNameStringToEnum(String descriptorSubclass) {
        final Optional<DescriptorLanguageSubclass> first = Arrays.stream(DescriptorLanguageSubclass.values())
            .filter(subclass -> subclass.getShortName().equalsIgnoreCase(descriptorSubclass)).findFirst();
        return first.orElseThrow(() -> new UnsupportedOperationException("language not supported yet"));
    }
}
