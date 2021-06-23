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

import java.net.URL;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Complete interface for new languages
 *
 */
public interface CompleteLanguageInterface extends RecommendedLanguageInterface {

    /**
     * Process a workflow and generate a cytoscape compatible data structure
     * @param initialPath  the path to the primary descriptor
     * @param contents     contents of the primary descriptor
     * @param indexedFiles the set of files indexed from MinimalLanguageInterface
     * @return cytoscape compatible data structure http://manual.cytoscape.org/en/stable/Supported_Network_File_Formats.html#cytoscape-js-json
     */
    Map<String, Object> loadCytoscapeElements(String initialPath, String contents, Map<String, Pair<String, GenericFileType>> indexedFiles);

    /**
     * Generate table containing information on the steps of the workflow, potentially including ids, URLs to more information, Docker containers
     * @param initialPath  the path to the primary descriptor
     * @param contents     contents of the primary descriptor
     * @param indexedFiles the set of files indexed from MinimalLanguageInterface
     * @return table with row type data
     */
    List<RowData> generateToolsTable(String initialPath, String contents, Map<String, Pair<String, GenericFileType>> indexedFiles);

    /**
     * One row of the table per unique workflow step (i.e. do not need to create muliple elements for scattered operations
     */
    class RowData {
        public RowType rowType;
        public String toolid;
        public String label;
        // not applicable for Galaxy
        public String filename;
        public URL link;
        // not necessarily applicable for Galaxy
        public String dockerContainer;
    }

    /**
     * A step of a workflow can be a subworkflow (i.e. not one unique dockerContainer, tool, etc.)
     */
    enum RowType {
        TOOL,
        SUBWORKFLOW
    }


}
