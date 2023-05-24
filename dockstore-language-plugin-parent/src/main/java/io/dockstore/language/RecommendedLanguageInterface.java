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
package io.dockstore.language;

import io.dockstore.common.VersionTypeValidation;
import java.util.Map;

/**
 * Recommended interface for new workflow languages.
 * <p>
 * Unstructured information that we would like to know
 * 1) Grammar for your language in order to highlight your language so it looks nice for users. We use <a href="https://ace.c9.io/#nav=higlighter">Ace</a>
 * but have some experience converting from ( <a href="https://github.com/github/linguist">Linguist grammars</a> )
 */
public interface RecommendedLanguageInterface extends MinimalLanguageInterface {

    /**
     * These are relatively freeform instructions, given a TRS ID that corresponds to the TRS representation
     * of your workflow, how do we launch your workflow.
     * Consider an ID of "#workflow/github.com/ICGC-TCGA-PanCancer/wdl-pcawg-sanger-cgp-workflow" leading to https://dockstore.org/api/api/ga4gh/v2/tools/%23workflow%2Fgithub.com%2FICGC-TCGA-PanCancer%2Fwdl-pcawg-sanger-cgp-workflow
     *
     * @param trsID ID in the standardized GA4GH TRS
     * @return Markdown instructions on how to run a workflow once it is in Dockstore
     */
    String launchInstructions(String trsID);

    /**
     * Given a set of indexed files from MinimalLanguageInterface, says whether the workflow is valid
     *
     * @param initialPath  the path to the primary descriptor
     * @param contents     contents of the primary descriptor
     * @param indexedFiles the set of files indexed from MinimalLanguageInterface
     * @return validity and if not, messages that would help a user debug
     */
    VersionTypeValidation validateWorkflowSet(String initialPath, String contents, Map<String, FileMetadata> indexedFiles);

    /**
     * Given a set of indexed files from MinimalLanguageInterface cut down to just test parameters, says whether the test
     * parameter files are valid
     *
     * @param indexedFiles the set of files indexed from MinimalLanguageInterface
     * @return validity and if not, messages that would help a user debug
     */
    VersionTypeValidation validateTestParameterSet(Map<String, FileMetadata> indexedFiles);
}
