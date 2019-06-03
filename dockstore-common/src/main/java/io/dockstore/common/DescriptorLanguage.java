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

import java.util.Arrays;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

/**
 * This enumerates the types of descriptor language that we can associate an entry with.
 *
 * @author agduncan94
 */
public enum DescriptorLanguage {
    // Add new descriptor language here
    CWL("CWL", "Common Workflow Language", FileType.DOCKSTORE_CWL, FileType.CWL_TEST_JSON) {
        @Override
        public boolean isRelevantFileType(FileType type) {
            return super.isRelevantFileType(type) || type == FileType.DOCKERFILE;
        }
    },
    WDL("WDL", "Workflow Description Language", FileType.DOCKSTORE_WDL, FileType.WDL_TEST_JSON) {
        @Override
        public boolean isRelevantFileType(FileType type) {
            return super.isRelevantFileType(type) || type == FileType.DOCKERFILE;
        }
    },
    // DOCKSTORE-2428 - demo how to add new workflow language
    //SWL("SWL", "Silly Workflow Language", FileType.DOCKSTORE_SWL, FileType.SWL_TEST_JSON)
    NEXTFLOW("NFL", "Nextflow", FileType.NEXTFLOW_CONFIG, FileType.NEXTFLOW_TEST_PARAMS) {
        @Override
        public boolean isRelevantFileType(FileType type) {
            return super.isRelevantFileType(type) || type == FileType.DOCKERFILE || type == FileType.NEXTFLOW;
        }
    },
    SERVICE("service", "generic placeholder for services", FileType.DOCKSTORE_SERVICE_YML, FileType.DOCKSTORE_SERVICE_TEST_JSON);

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

    /**
     * This indicates that this language is for services
     */
    private final boolean serviceLanguage;

    DescriptorLanguage(final String shortName, final String friendlyName, final FileType fileType, final FileType testParamType) {
        this(shortName, friendlyName, fileType, testParamType, false);
    }

    DescriptorLanguage(final String shortName, final String friendlyName, final FileType fileType, final FileType testParamType, final boolean serviceLanguage) {
        this.shortName = shortName;
        this.friendlyName = friendlyName;
        this.fileType = fileType;
        this.testParamType = testParamType;
        this.serviceLanguage = serviceLanguage;
    }



    @Override
    public String toString() {
        return shortName;
    }

    public String getShortName() {
        return shortName;
    }

    public String getLowerShortName() {
        return shortName.toLowerCase();
    }

    public String getFriendlyName() {
        return friendlyName;
    }

    public static DescriptorLanguage convertShortStringToEnum(String descriptor) {
        final Optional<DescriptorLanguage> first = Arrays.stream(DescriptorLanguage.values())
            .filter(lang -> lang.getShortName().equalsIgnoreCase(descriptor)).findFirst();
        return first.orElseThrow(() -> new UnsupportedOperationException("language not supported yet"));
    }

    public FileType getFileType() {
        return fileType;
    }

    public static Optional<FileType> getFileType(String descriptorType) {
        // this is tricky, since it is used by GA4GH, those APIs can use string of the form PLAIN_CWL
        // which is why we use StringUtils.containsIgnoreCase
        return Arrays.stream(DescriptorLanguage.values())
            .filter(lang -> StringUtils.containsIgnoreCase(descriptorType, lang.toString())).findFirst().map(DescriptorLanguage::getFileType);
    }

    public FileType getTestParamType() {
        return testParamType;
    }

    public static Optional<FileType> getTestParameterType(String descriptorType) {
        if (descriptorType == null) {
            return Optional.empty();
        }
        return Arrays.stream(DescriptorLanguage.values()).filter(lang -> descriptorType.equalsIgnoreCase(lang.toString())).findFirst().map(DescriptorLanguage::getTestParamType);
    }

    public boolean isServiceLanguage() {
        return serviceLanguage;
    }

    public boolean isRelevantFileType(FileType type) {
        return type == fileType || type == testParamType;
    }

    /**
     * NextFlow parameter files are described here https://github.com/nextflow-io/nextflow/issues/208
     *
     */
    public enum FileType {
        // Add supported descriptor types here
        DOCKSTORE_CWL, DOCKSTORE_WDL, DOCKERFILE, CWL_TEST_JSON, WDL_TEST_JSON, NEXTFLOW, NEXTFLOW_CONFIG, NEXTFLOW_TEST_PARAMS, DOCKSTORE_YML, DOCKSTORE_SERVICE_YML, DOCKSTORE_SERVICE_TEST_JSON
        // DOCKSTORE_SWL, SWL_TEST_JSON
        // DOCKSTORE-2428 - demo how to add new workflow language
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
