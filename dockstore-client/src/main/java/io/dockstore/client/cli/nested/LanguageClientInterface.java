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
package io.dockstore.client.cli.nested;

import java.io.File;
import java.io.IOException;

import io.swagger.client.ApiException;

/**
 * Documents similar methods between language clients
 */
public interface LanguageClientInterface {

    /**
     * Check for valid content of the particular language
     * @param content the content of the main descriptor
     * @return true if the file in in the language handled by this
     */
    Boolean check(File content);

    /**
     * Download secondary files for an entry
     * @param entry the path to the downloaded primary file
     * @param json return in json? retained from before refactoring, but this parameter seems pointless
     * @return
     * @throws ApiException
     * @throws IOException
     */
    String generateInputJson(String entry, boolean json) throws ApiException, IOException;

    /**
     * Merged launch interface, definitely needs cleanup.
     * wdlOutputTarget is obviously only applicable to Cromwell
     * yamlRun and csvRuns only implemented for CWL
     *
     * @param entry        either a dockstore entry or a local file
     * @param isLocalEntry is the descriptor a local file
     * @param yamlRun      runtime descriptor, one of these is required
     * @param jsonRun      runtime descriptor, one of these is required
     * @param csvRuns      runtime descriptor, one of these is required
     * @param wdlOutputTarget directory where to drop off output for wdl
     * @param uuid         uuid that was optional specified for notifications
     * @throws IOException
     * @throws ApiException
     */
    long launch(String entry, boolean isLocalEntry, String yamlRun, String jsonRun, String csvRuns, String wdlOutputTarget, String uuid) throws IOException, ApiException;
}
