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
    public static final String GA4GHV20 = "A curated subset of resources proposed as a common standard for tool repositories. Implements TRS [2.0.0](https://github.com/ga4gh/tool-registry-service-schemas/releases/tag/2.0.0).";
    public static final String EXTENDEDGA4GH = "Optional experimental extensions of the GA4GH API";
    public static final String TOKENS = "List, modify, refresh, and delete tokens for external services";
    public static final String WORKFLOWS = "List and register workflows in the dockstore (CWL, Nextflow, WDL)";
    public static final String HOSTED = "Created and modify hosted entries in the dockstore";
    public static final String USERS = "List, modify, and manage end users of the dockstore";
    public static final String METADATA = "Information about Dockstore like RSS, sitemap, lists of dependencies, etc.";
    public static final String TOOLTESTER = "Interactions with the Dockstore-support's ToolTester application";
    public static final String ORGANIZATIONS = "Operations on Dockstore organizations";
    public static final String CATEGORIES = "Operations on Dockstore categories";
    public static final String CURATION = "List and modify notifications for users of Dockstore";
    public static final String NIHDATACOMMONS = "Needed for SmartAPI compatibility apparantly, might be cargo cult behaviour";
    public static final String LAMBDAEVENTS = "Query lambda events triggered by GitHub Apps";
    public static final String OPENAPI_JWT_SECURITY_DEFINITION_NAME = "bearer";
    public static final String APPEASE_SWAGGER_PATCH = "This is here to appease Swagger. It requires PATCH methods to have a body, even if it is empty. Please leave it empty.";
    public static final String PAGINATION_LIMIT = "100";
    public static final int VERSION_PAGINATION_LIMIT = 200;
    public static final String PAGINATION_LIMIT_TEXT = "Amount of records to return in a given page, limited to " + PAGINATION_LIMIT;
    public static final String PAGINATION_OFFSET_TEXT = "Start index of paging. Pagination results can be based on numbers or other values chosen by the registry implementor (for example, SHA values). If this exceeds the current result set return an empty set.  If not specified in the request, this will start at the beginning of the results.";

    private ResourceConstants() {
        // utility class
    }
}
