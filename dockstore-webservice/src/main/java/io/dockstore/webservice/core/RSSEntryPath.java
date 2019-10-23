/*
 * Copyright 2019 OICR
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.dockstore.webservice.core;

import java.util.Date;

/**
 * @author gluu
 * @since 2019-10-22
 */
public abstract class RSSEntryPath {
    protected Date lastUpdated;
    protected String description;
    protected String entryName;
    public abstract String getEntryPath();

    public String getURLPath(String entryType) {
        return String.format("/%ss/%s", entryType, getEntryPath());
    }

    public String getDescription() {
        return description;
    }

    public Date getLastUpdated() {
        if (lastUpdated == null) {
            return new Date(0L);
        }
        return lastUpdated;
    }
}
