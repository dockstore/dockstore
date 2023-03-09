/*
 * Copyright 2023 OICR and UCSC
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

import java.util.Set;

/**
 * <p>Determines the openness of a set of urls. An open URL:</p>
 *
 * <ul>
 *     <li>Has a protocol recognized out of the box by Java. Note that this does NOT include S3
 *     and GS URIs</li>
 *     <li>Is not a local file; caveat to above, as file:// is a protocol recognized by Java</li>
 *     <li>Is accessible without authentication</li>
 * </ul>
 *
 */
public interface CheckUrlInterface {

    /**
     * Checks whether the entire set of <code>possibleUrls</code> is open or not. If the set is
     * empty, the implementation should return <code>ALL_OPEN</code>.
     * @param possibleUrls
     * @return
     */
    UrlStatus checkUrls(Set<String> possibleUrls);

    /**
     * The status of a set of urls.
     */
    enum UrlStatus {
        /**
         * All urls are open access.
         */
        ALL_OPEN,
        /**
         * At least one of the urls is not open access.
         */
        NOT_ALL_OPEN,
        /**
         * There was an error determining the status of urls.
         */
        UNKNOWN,
    }
}
