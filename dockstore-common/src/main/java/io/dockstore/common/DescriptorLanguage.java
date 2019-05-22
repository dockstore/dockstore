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

import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

/**
 * This enumerates the types of descriptor language that we can associate an entry with.
 *
 * @author agduncan94
 */
public enum DescriptorLanguage {
    // Add new descriptor language here
    CWL("CWL", "Common Workflow Language", FileType.DOCKSTORE_CWL, FileType.CWL_TEST_JSON),
    WDL("WDL", "Workflow Description Language", FileType.DOCKSTORE_WDL, FileType.WDL_TEST_JSON),
    // DOCKSTORE-2428 - demo how to add new workflow language
    //SWL("SWL", "Silly Workflow Language", FileType.DOCKSTORE_SWL, FileType.SWL_TEST_JSON)
    NEXTFLOW("NFL", "Nextflow", FileType.NEXTFLOW_CONFIG, FileType.NEXTFLOW_TEST_PARAMS);

    public static final String CWL_STRING = "cwl";
    public static final String WDL_STRING = "wdl";
    public static final String NFL_STRING = "nfl";
    // DOCKSTORE-2428 - demo how to add new workflow language
    // public static final String SWL_STRING = "swl";
    /**
     * this name is used in the workflow path
     */
    private final String shortName;

    /**
     * this name is what is displayed to users to name the descriptor language
     */
    private final String friendlyName;

    /**
     * This is the primary descriptor filetype stored for files of this language in the database
     */
    private final FileType fileType;

    /**
     * This is the type for the test parameter file for this language
     */
    private final FileType testParamType;

    DescriptorLanguage(final String shortName, final String friendlyName, final FileType fileType, final FileType testParamType) {
        this.shortName = shortName;
        this.friendlyName = friendlyName;
        this.fileType = fileType;
        this.testParamType = testParamType;
    }

    @Override
    public String toString() {
        return shortName;
    }

    public String getFriendlyName() {
        return friendlyName;
    }

    public static DescriptorLanguage convertShortStringToEnum(String descriptor) {
        String lowerDescriptor = descriptor.toLowerCase();
        switch (lowerDescriptor) {
        case CWL_STRING:
            return CWL;
        case WDL_STRING:
            return WDL;
        case NFL_STRING:
            return NEXTFLOW;
        // DOCKSTORE-2428 - demo how to add new workflow language
        //
        //case SWL_STRING:
        //    return SWL;
        default:
            // fall-through and throw exception
        }
        throw new UnsupportedOperationException("language not supported yet");
    }

    public FileType getFileType() {
        return fileType;
    }

    public static Optional<FileType> getFileType(String descriptorType) {
        for (DescriptorLanguage lang : values()) {
            // this is tricky, since it is used by GA4GH, those APIs can use string of the form PLAIN_CWL
            if ((StringUtils.containsIgnoreCase(descriptorType, lang.toString()))) {
                return Optional.of(lang.getFileType());
            }
        }
        return Optional.empty();
    }

    public FileType getTestParamType() {
        return testParamType;
    }

    public static Optional<FileType> getTestParameterType(String descriptorType) {
        for (DescriptorLanguage lang : values()) {
            if (descriptorType.equalsIgnoreCase(lang.toString())) {
                return Optional.of(lang.getTestParamType());
            }
        }
        return Optional.empty();
    }

    /**
     * NextFlow parameter files are described here https://github.com/nextflow-io/nextflow/issues/208
     *
     */
    public enum FileType {
        // Add supported descriptor types here
        DOCKSTORE_CWL, DOCKSTORE_WDL, DOCKERFILE, CWL_TEST_JSON, WDL_TEST_JSON, NEXTFLOW, NEXTFLOW_CONFIG, NEXTFLOW_TEST_PARAMS, DOCKSTORE_YML
        // DOCKSTORE-2428 - demo how to add new workflow language
        //,     DOCKSTORE_SWL, SWL_TEST_JSON
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
