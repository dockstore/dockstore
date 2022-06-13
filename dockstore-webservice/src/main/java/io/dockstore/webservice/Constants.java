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
package io.dockstore.webservice;

import java.util.List;
import java.util.regex.Pattern;

/**
 * @author gluu
 * @since 22/09/17
 */
public final class Constants {
    public static final int LAMBDA_FAILURE = 418; // Tell lambda to not try again
    public static final String OPTIONAL_AUTH_MESSAGE = "Does not require authentication for published workflows,"
            + " authentication can be provided for restricted workflows";
    public static final String DOCKSTORE_YML_PATH = "/.dockstore.yml";
    public static final List<String> DOCKSTORE_YML_PATHS = List.of(DOCKSTORE_YML_PATH, "/.github/.dockstore.yml");
    public static final String SKIP_COMMIT_ID = "skip";
    public static final Pattern AMAZON_ECR_PRIVATE_REGISTRY_REGEX = Pattern.compile("^[a-zA-Z0-9]+\\.dkr\\.ecr\\.[a-zA-Z0-9-]+\\.amazonaws\\.com");
    public static final String USERNAME_CHANGE_REQUIRED = "Your username contains one or more of the following keywords: dockstore, admin, curator, system, or manager. "
        + "Several operations will be blocked until you change your username via the Accounts page.";
    public static final Pattern USERNAME_CONTAINS_KEYWORD_PATTERN = Pattern.compile("(?i)(dockstore|admin|curator|system|manager)");

    private Constants() {
        // not called
    }
}
