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
 * TODO: DAG interface methods
 */
public interface CompleteLanguageInterface extends RecommendedLanguageInterface {

    CytoscapeObject generateDAG(String initialPath, String contents, Map<String, Pair<String, GenericFileType>> indexedFiles);

    List<RowData> generateToolsTable(String initialPath, String contents, Map<String, Pair<String, GenericFileType>> indexedFiles);

    class RowData {
        public RowType rowType;
        public String toolid;
        public String label;
        // not applicable for Galaxy
        public String filename;
        public URL link;
        public String dockerContainer;
    }

    enum RowType {
        TOOL,
        SUBWORKFLOW
    }


}
