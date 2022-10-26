/*
 * Copyright 2022 OICR and UCSC
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
 */

package io.dockstore.common;

import java.util.regex.Pattern;

/**
 * This file describes the constraints on various Dockstore inputs.
 */
public final class ValidationConstants {

    public static final String ENTRY_NAME_REGEX = "[a-zA-Z0-9]+([-_][a-zA-Z0-9]+)*+";
    public static final Pattern ENTRY_NAME_PATTERN = Pattern.compile(ENTRY_NAME_REGEX);
    public static final String ENTRY_NAME_REGEX_MESSAGE = "must contain only letters, numbers, and internal hyphens and underscores";
    public static final int ENTRY_NAME_LENGTH_MIN = 1;
    public static final int ENTRY_NAME_LENGTH_MAX = 256;
    public static final String ENTRY_NAME_LENGTH_MESSAGE = "must be non-empty and 256 characters or less";

    // Valid ORCIDs can end with 'X':
    // https://support.orcid.org/hc/en-us/articles/360053289173-Why-does-my-ORCID-iD-have-an-X-
    // Stephen Hawking's ORCID: https://orcid.org/0000-0002-9079-593X
    public static final String ORCID_ID_REGEX = "\\d{4}-\\d{4}-\\d{4}-\\d{3}[X\\d]";
    public static final Pattern ORCID_ID_PATTERN = Pattern.compile(ORCID_ID_REGEX);

    private ValidationConstants() {
        // hide the default constructor for a constant class
    }
}
