/*
 * Copyright 2023 OICR and UCSC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *           http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.dockstore.webservice.core;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class EntryNamesConverter extends DelimitedValuesConverter {
    /**
     * Descriptor types are stored in the database as a string and are comma separated.
     */

    public EntryNamesConverter() {
        super(",");
    }

    @Override
    public String getSubject(boolean isPlural) {
        return "Entry path" + (isPlural ? "s" : "");
    }
}
