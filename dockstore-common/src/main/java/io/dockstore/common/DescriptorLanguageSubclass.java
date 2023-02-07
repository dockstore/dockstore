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

import static io.dockstore.common.EntryType.APPTOOL;
import static io.dockstore.common.EntryType.NOTEBOOK;
import static io.dockstore.common.EntryType.SERVICE;
import static io.dockstore.common.EntryType.TOOL;
import static io.dockstore.common.EntryType.WORKFLOW;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

public enum DescriptorLanguageSubclass {
    DOCKER_COMPOSE("docker-compose", SERVICE),
    HELM("helm", SERVICE),
    SWARM("swarm", SERVICE),
    KUBERNETES("kubernetes", SERVICE),

    PYTHON("Python", NOTEBOOK),
    R("R", NOTEBOOK),
    JAVASCRIPT("Javascript", NOTEBOOK),
    SCALA("Scala", NOTEBOOK),
    JULIA("Julia", NOTEBOOK),

    NOT_APPLICABLE("n/a", Set.of(SERVICE, WORKFLOW, APPTOOL, TOOL));

    private final String shortName;

    private Set<EntryType> entryTypes;

    DescriptorLanguageSubclass(final String shortName, Set<EntryType> entryTypes) {
        this.shortName = shortName;
        this.entryTypes = entryTypes;
    }

    DescriptorLanguageSubclass(final String shortName, EntryType entryType) {
        this(shortName, Set.of(entryType));
    }

    public boolean isApplicable() {
        return this != NOT_APPLICABLE;
    }

    @Override
    public String toString() {
        return shortName;
    }

    @JsonValue
    public String getShortName() {
        return shortName;
    }

    public Set<EntryType> getEntryTypes() {
        return entryTypes;
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
