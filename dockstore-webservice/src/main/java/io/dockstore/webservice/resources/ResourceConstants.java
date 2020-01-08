/*
 *    Copyright 2019 OICR
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
package io.dockstore.webservice.resources;

public final class ResourceConstants {
    public static final String ALIASES = "Create, update list aliases for accessing entries";
    public static final String ENTRIES = "Interact with entries in Dockstore regardless of whether they are containers or workflows";
    public static final String CONTAINERS = "List and register entries in the dockstore (pairs of images + metadata (CWL and Dockerfile))";
    public static final String CONTAINERTAGS = "List and modify tags for containers";
    public static final String GA4GHV1 = "A curated subset of resources proposed as a common standard for tool repositories. Implements TRS [1.0.0](https://github.com/ga4gh/tool-registry-service-schemas/releases/tag/1.0.0) and is considered final (not subject to change)";
    public static final String GA4GH = "A curated subset of resources proposed as a common standard for tool repositories. Implements TRS [2.0.0-beta.2](https://github.com/ga4gh/tool-registry-service-schemas/releases/tag/2.0.0-beta.2) . Integrators are welcome to use these endpoints but they are subject to change based on community input.";
    public static final String EXTENDEDGA4GH = "Optional experimental extensions of the GA4GH API";
    public static final String TOKENS = "List, modify, refresh, and delete tokens for external services";
    public static final String WORKFLOWS = "List and register workflows in the dockstore (CWL or WDL)";
    public static final String HOSTED = "Created and modify hosted entries in the dockstore";
    public static final String USERS = "List, modify, and manage end users of the dockstore";
    public static final String METADATA = "Information about Dockstore like RSS, sitemap, lists of dependencies, etc.";
    public static final String TOOLTESTER = "Interactions with the Dockstore-support's ToolTester application";
    public static final String ORGANIZATIONS = "Operations on Dockstore organizations";
    public static final String CURATION = "List and modify notifications for users of Dockstore";

    private ResourceConstants() {
        // utility class
    }
}
