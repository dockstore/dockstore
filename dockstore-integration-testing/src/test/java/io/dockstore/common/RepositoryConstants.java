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

package io.dockstore.common;

public final class RepositoryConstants {

    private RepositoryConstants() {
        // hide the default constructor for a constant class
    }

    public static class DockstoreTestUser2 {
        public static final String DOCKSTORE_WORKFLOW_CNV = "DockstoreTestUser2/dockstore_workflow_cnv";
        public static final String DOCKSTOREYML_GITHUB_FILTERS_TEST = "DockstoreTestUser2/dockstoreyml-github-filters-test";
        public static final String TEST_AUTHORS = "DockstoreTestUser2/test-authors";
        public static final String TEST_SERVICE = "DockstoreTestUser2/test-service";
        public static final String TEST_WORKFLOW_AND_TOOLS = "DockstoreTestUser2/test-workflows-and-tools";
        public static final String TEST_WORKFLOW_AND_TOOLS_TOOL_PATH = TEST_WORKFLOW_AND_TOOLS + "/md5sum";
        public static final String WORKFLOW_DOCKSTORE_YML = "DockstoreTestUser2/workflow-dockstore-yml";
        // Contains a Galaxy workflow
        public static final String WORKFLOW_TESTING_REPO = "DockstoreTestUser2/workflow-testing-repo";
    }

    public static class DockstoreTesting {
        public static final String MULTI_ENTRY = "dockstore-testing/multi-entry";
        // Repository with a large .dockstore.yml and alot of branches
        public static final String RODENT_OF_UNUSUAL_SIZE = "dockstore-testing/rodent-of-unusual-size";
        public static final String TAGGED_APPTOOL = "dockstore-testing/tagged-apptool";
        public static final String TAGGED_APPTOOL_TOOL_PATH = TAGGED_APPTOOL + "/md5sum";
        public static final String TEST_WORKFLOWS_AND_TOOLS = "dockstore-testing/test-workflows-and-tools";
        public static final String WDL_HUMANWGS = "dockstore-testing/wdl-humanwgs";
        public static final String WORKFLOW_DOCKSTORE_YML = "dockstore-testing/workflow-dockstore-yml";
        public static final String TEST_SERVICE = "dockstore-testing/test-service";
    }
}
