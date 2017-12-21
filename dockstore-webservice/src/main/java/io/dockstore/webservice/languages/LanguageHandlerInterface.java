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
package io.dockstore.webservice.languages;

import java.util.Map;

import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Version;

/**
 * This interface will be the future home of all methods that will need to be added to support a new workflow language
 */
public interface LanguageHandlerInterface {

    /**
     * Parses the content of the primary descriptor to get author, email, and description
     * @param entry an entry to be updated
     * @param content a cwl document
     * @return the updated entry
     */
    Entry parseWorkflowContent(Entry entry, String content);

    /**
     * Confirms whether the content of a descriptor contains a valid workflow
     * @param content the content of a descriptor
     * @return true iff the workflow looks like a valid workflow
     */
    boolean isValidWorkflow(String content);

    /**
     * Look at the content of a descriptor and update its imports
     * @param content content of the primary descriptor
     * @param version version of the files to get
     * @return map of file paths to SourceFile objects
     */
    Map<String, SourceFile> processImports(String content, Version version);
}
