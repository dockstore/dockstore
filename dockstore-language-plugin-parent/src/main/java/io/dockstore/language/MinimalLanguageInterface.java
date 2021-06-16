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
package io.dockstore.language;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import io.dockstore.common.DescriptorLanguage;
import org.apache.commons.lang3.tuple.Pair;
import org.pf4j.ExtensionPoint;

/**
 * Minimal interface for new workflow languages
 * This collects the kind of information that we would like to collect on a workflow language.
 * <p>
 * We would also like to know the following information which is less likely to be common between types of workflow languages
 * a) A logo for your language
 * b) Launch instructions for your language (i.e. given all the desciptors for a workflow locally, how would I run a workflow on the command-line?)
 * c) What language is your description in (Markdown, HTML, etc.)?
 * d) A short (acronym like CWl, WDL) name for your language and a longer more descriptive name (like "Common Workflow Language")
 */
public interface MinimalLanguageInterface extends ExtensionPoint {

    DescriptorLanguage getDescriptorLanguage();

    /**
     * Validate a filename path that might be entered by your user (i.e. is "/Dockstore.wdl" a valid path to the "first" descriptor).
     * This is used to distinguish between multiple workflows that may be stored in one repo.
     *
     * @return a pattern that can be used to validate filepaths
     */
    Pattern initialPathPattern();

    /**
     * Find all files that would be useful to display to a user (hopefully all files needed to launch a workflow)
     *
     * @param initialPath path to the intial descriptor as set by the user (e.g. /example_directory/Dockstore.cwl)
     * @param contents    contents of the initial descriptor
     * @param reader      get additional files and their content
     * @return a map from absolute paths (relative to the root of the repo, e.g. "common.cwl" as opposed to "../common.cwl") to their file types and content
     */
    Map<String, Pair<String, GenericFileType>> indexWorkflowFiles(String initialPath, String contents, FileReader reader);

    /**
     * Given the primary descriptor and the files indexed from indexWorkflowFiles, return relevant metadata
     * about the workflow.
     *
     * @param initialPath  the path to the primary descriptor
     * @param contents     contents of the primary descriptor
     * @param indexedFiles the set of files indexed above
     * @return the workflow metadata that we will show to users
     */
    WorkflowMetadata parseWorkflowForMetadata(String initialPath, String contents, Map<String, Pair<String, GenericFileType>> indexedFiles);

    /**
     * When indexing, Dockstore will distinguish between extra files that hold things like extra code, tools, configuration (imported descriptors) and test parameter files (example parameter sets used to run a workflow)
     */
    enum GenericFileType { IMPORTED_DESCRIPTOR, TEST_PARAMETER_FILE, CONTAINERFILE
    }

    /**
     * Reads files relative to the file found via {@link #initialPathPattern()}
     */
    interface FileReader {
        /**
         * Get the contents of a file from GitHub, GitLab, etc.
         *
         * @param path relative to the primary descriptor, the path
         * @return contents of the file at path
         */
        String readFile(String path);

        /**
         * Get list of files in a directory (non-recursive) relative to the initial path
         * TODO: underlying interface should return whether files are directories, but we seem to have gotten away with it so far
         * @return return list of files
         */
        List<String> listFiles(String pathToDirectory);
    }

    /**
     * Information that can be parsed from a specific version of a workflow and would be useful to display to users.
     * This metadata will be displayed in a variety of ways via the dockstore UI (default metadata for the whole workflow,
     * workflow version specific metadata might be displayed on pages/DOIs for specific versions)
     */
    class WorkflowMetadata {
        private String author;
        private String description;
        private String email;

        public String getAuthor() {
            return author;
        }

        public void setAuthor(String author) {
            this.author = author;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }
    }
}
