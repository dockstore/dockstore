/*
 *
 *    Copyright 2020 OICR
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
 *
 */

package io.dockstore.common;

import java.util.Arrays;
import java.util.Optional;

public enum DescriptorLanguageSubclass {
    DOCKER_COMPOSE("docker-compose"),
    HELM("helm"),
    SWARM("swarm"),
    KUBERNETES("kubernetes"),

    PYTHON("python"),
    R("r"),
    JULIA("julia"),

    NOT_APPLICABLE("n/a");

    private final String shortName;

    DescriptorLanguageSubclass(final String shortName) {
        this.shortName = shortName;
    }

    public boolean isApplicable() {
        return this != NOT_APPLICABLE;
    }

    @Override
    public String toString() {
        return shortName;
    }

    public String getShortName() {
        return shortName;
    }

    public static DescriptorLanguageSubclass convertShortNameStringToEnum(final String descriptorSubclass) {
        if (descriptorSubclass == null) {
            return null;
        }
        final Optional<DescriptorLanguageSubclass> first = Arrays.stream(DescriptorLanguageSubclass.values())
            .filter(subclass -> subclass.getShortName().equalsIgnoreCase(descriptorSubclass)).findFirst();
        return first.orElseThrow(() -> new UnsupportedOperationException("language not supported yet"));
    }
}
