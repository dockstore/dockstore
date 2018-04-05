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

package io.dockstore.common;

/**
 * This enumerates the types of descriptor language that we can associate an entry with.
 *
 * @author agduncan94
 */
public enum DescriptorLanguage {
    // Add new descriptor language here
    CWL("CWL", "Common Workflow Language"), WDL("WDL", "Workflow Description Language"), NEXTFLOW("NEXTFLOW", "Nextflow");

    /**
     * this name is used in the workflow path
     */
    private final String descriptorLanguageShort;

    /**
     * this name is what is displayed to users to name the descriptor language
     */
    private final String friendlyName;

    DescriptorLanguage(final String descriptorLanguageShort, final String friendlyName) {
        this.descriptorLanguageShort = descriptorLanguageShort;
        this.friendlyName = friendlyName;
    }

    @Override
    public String toString() {
        return descriptorLanguageShort;
    }

    public String getFriendlyName() {
        return friendlyName;
    }

    /**
     * Expanded version for API list of descriptor language
     */
    public static class DescriptorLanguageBean {

        public String value;

        public String friendlyName;

        public DescriptorLanguageBean(DescriptorLanguage descriptorLanguage) {
            this.value = descriptorLanguage.toString();
            this.friendlyName = descriptorLanguage.getFriendlyName();
        }
    }
}
