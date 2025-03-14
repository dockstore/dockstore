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

import static io.dockstore.common.EntryType.APPTOOL;
import static io.dockstore.common.EntryType.NOTEBOOK;
import static io.dockstore.common.EntryType.TOOL;
import static io.dockstore.common.EntryType.WORKFLOW;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;

/**
 * This enumerates the types of descriptor language that we can associate an entry with.
 *
 * @author agduncan94
 */
// @Schema(enumAsRef = true)
public enum DescriptorLanguage {
    // Add new descriptor language here
    // RE: Snakemake defaultPrimaryDescriptorExtensions: The root descriptor for Snakemake
    // should be called 'Snakefile' (no suffix), the other descriptors should have the '.smk' suffix.
    // https://snakemake.readthedocs.io/en/stable/snakefiles/deployment.html
    SMK("SMK", "Snakemake", FileType.DOCKSTORE_SMK, FileType.SMK_TEST_PARAMS, false, true,
            Set.of("Snakefile", "yaml", "yml"), true, true, Set.of(WORKFLOW)) {
        @Override
        public boolean isRelevantFileType(FileType type) {
            return super.isRelevantFileType(type) || type == FileType.DOCKSTORE_YML;
        }
    },
    CWL("CWL", "Common Workflow Language", FileType.DOCKSTORE_CWL, FileType.CWL_TEST_JSON, false, false,
        Set.of("cwl", "yaml", "yml"), true, true, Set.of(WORKFLOW, APPTOOL, TOOL)) {
        @Override
        public boolean isRelevantFileType(FileType type) {
            return super.isRelevantFileType(type) || type == FileType.DOCKERFILE || type == FileType.DOCKSTORE_YML;
        }
    },
    WDL("WDL", "Workflow Description Language", FileType.DOCKSTORE_WDL, FileType.WDL_TEST_JSON, false, false, Set.of("wdl"), true, true, Set.of(WORKFLOW, APPTOOL, TOOL)) {
        @Override
        public boolean isRelevantFileType(FileType type) {
            return super.isRelevantFileType(type) || type == FileType.DOCKERFILE || type == FileType.DOCKSTORE_YML;
        }
    },
    GXFORMAT2("gxformat2", "Galaxy Workflow Format 2", FileType.DOCKSTORE_GXFORMAT2, FileType.GXFORMAT2_TEST_FILE, false, true,
        Set.of("ga", "yaml", "yml"), true, false, Set.of(WORKFLOW)) {
        @Override
        public boolean isRelevantFileType(FileType type) {
            return super.isRelevantFileType(type) || type == FileType.DOCKSTORE_YML;
        }
    },
    // DOCKSTORE-2428 - demo how to add new workflow language
    SWL("SWL", "Silly Workflow Language", FileType.DOCKSTORE_SWL, FileType.SWL_TEST_JSON, false, true, Set.of("swl"), false, false, Set.of(WORKFLOW, APPTOOL, TOOL)),
    NEXTFLOW("NFL", "Nextflow", FileType.NEXTFLOW_CONFIG, FileType.NEXTFLOW_TEST_PARAMS, false, false, Set.of("config"), true, false, Set.of(WORKFLOW)) {
        @Override
        public boolean isRelevantFileType(FileType type) {
            return super.isRelevantFileType(type) || type == FileType.DOCKERFILE || type == FileType.NEXTFLOW || type == FileType.DOCKSTORE_YML;
        }
    },
    SERVICE("service", "generic placeholder for services", FileType.DOCKSTORE_SERVICE_YML, FileType.DOCKSTORE_SERVICE_TEST_JSON, true,
        false, Set.of("yml"), true, false, Set.of(EntryType.SERVICE)) {
        @Override
        public boolean isRelevantFileType(FileType type) {
            return super.isRelevantFileType(type) || type == FileType.DOCKSTORE_SERVICE_OTHER;
        }
    },
    JUPYTER("jupyter", "Jupyter .ipynb notebook", FileType.DOCKSTORE_JUPYTER, FileType.DOCKSTORE_NOTEBOOK_TEST_FILE, false, false, Set.of("ipynb"), false, false, Set.of(NOTEBOOK)) {
        @Override
        public boolean isRelevantFileType(FileType type) {
            return super.isRelevantFileType(type) || type == FileType.DOCKSTORE_NOTEBOOK_REES || type == FileType.DOCKSTORE_NOTEBOOK_DEVCONTAINER || type == FileType.DOCKSTORE_NOTEBOOK_OTHER;
        }
    };

    /**
     * this name is used in the workflow path
     */
    private final String shortName;

    /**
     * this name is what is displayed to users to name the descriptor language
     */
    private final String friendlyName;

    /**
     * This is the primary descriptor file type stored for files of this language in the database
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

    /**
     * This indicates that this language is handled by language plugin
     */
    private final boolean pluginLanguage;

    private final Set<String> defaultPrimaryDescriptorExtensions;

    private final boolean supportsHosted;

    private final boolean supportsChecker;

    private final Set<EntryType> entryTypes;

    @SuppressWarnings("checkstyle:ParameterNumber")
    DescriptorLanguage(final String shortName, final String friendlyName, final FileType fileType, final FileType testParamType, final boolean serviceLanguage, final boolean pluginLanguage, final Set<String> defaultPrimaryDescriptorExtensions, final boolean supportsHosted, final boolean supportsChecker, Set<EntryType> entryTypes) {
        this.shortName = shortName;
        this.friendlyName = friendlyName;
        this.fileType = fileType;
        this.testParamType = testParamType;
        this.serviceLanguage = serviceLanguage;
        this.pluginLanguage = pluginLanguage;
        this.defaultPrimaryDescriptorExtensions = defaultPrimaryDescriptorExtensions;
        this.supportsHosted = supportsHosted;
        this.supportsChecker = supportsChecker;
        this.entryTypes = entryTypes;
    }

    @Override
    public String toString() {
        return shortName;
    }

    @JsonValue
    public String getShortName() {
        return shortName;
    }

    public String getFriendlyName() {
        return friendlyName;
    }

    public static DescriptorLanguage convertShortStringToEnum(String descriptor) {
        // Tricky case for GALAXY because it doesn't match the rules of the other languages
        if (StringUtils.containsIgnoreCase(descriptor, "galaxy")) {
            return GXFORMAT2;
        }

        final Optional<DescriptorLanguage> first = Arrays.stream(DescriptorLanguage.values())
            .filter(lang -> lang.getShortName().equalsIgnoreCase(descriptor)).findFirst();
        return first.orElseThrow(() -> new UnsupportedOperationException("language not supported yet"));
    }

    public FileType getFileType() {
        return fileType;
    }

    public static Optional<FileType> getOptionalFileType(String descriptorType) {
        // Tricky case for GALAXY because it doesn't match the rules of the other languages
        if (StringUtils.containsIgnoreCase(descriptorType, "galaxy")) {
            return Optional.of(GXFORMAT2.fileType);
        }
        // this is tricky, since it is used by GA4GH, those APIs can use string of the form PLAIN_CWL
        // which is why we use StringUtils.containsIgnoreCase
        return Arrays.stream(DescriptorLanguage.values())
            .filter(lang -> StringUtils.containsIgnoreCase(descriptorType, lang.toString())).findFirst().map(DescriptorLanguage::getFileType);
    }

    public static FileType getTestFileTypeFromDescriptorLanguageString(String descriptorType) {
        return convertShortStringToEnum(descriptorType).getTestParamType();
    }

    public FileType getTestParamType() {
        return testParamType;
    }

    public boolean isServiceLanguage() {
        return serviceLanguage;
    }

    public boolean isRelevantFileType(FileType type) {
        return Objects.equals(type, fileType) || Objects.equals(type, testParamType);
    }

    public boolean isPluginLanguage() {
        return pluginLanguage;
    }

    public Set<String> getDefaultPrimaryDescriptorExtensions() {
        return defaultPrimaryDescriptorExtensions;
    }

    public boolean isSupportsChecker() {
        return supportsChecker;
    }

    public boolean isSupportsHosted() {
        return supportsHosted;
    }

    public Set<EntryType> getEntryTypes() {
        return entryTypes;
    }

    public enum FileTypeCategory {
        GENERIC_DESCRIPTOR, // for languages where we don't currently store in the DB whether a descriptor is primary (we should)
        PRIMARY_DESCRIPTOR,
        SECONDARY_DESCRIPTOR,
        TEST_FILE,
        CONTAINERFILE,
        CONFIGURATION_FILE,
        OTHER
    }

    public static DescriptorLanguage getDescriptorLanguage(DescriptorLanguage.FileType fileType) {
        for (DescriptorLanguage lang : DescriptorLanguage.values()) {
            if (lang.getFileType() == fileType || lang.getTestParamType() == fileType) {
                return lang;
            }
            // include FileTypes that aren't primary descriptors or test param files
            switch (fileType) {
            case NEXTFLOW:
                return DescriptorLanguage.NEXTFLOW;
            case DOCKSTORE_SERVICE_OTHER:
                return DescriptorLanguage.SERVICE;
            default:
                break;
            }
        }
        throw new UnsupportedOperationException("Unknown language");
    }

    /**
     * Nextflow parameter files are described here <a href="https://github.com/nextflow-io/nextflow/issues/208">...</a>
     *
     */
    public enum FileType {
        // Add supported descriptor types here
        DOCKSTORE_SMK(FileTypeCategory.GENERIC_DESCRIPTOR), SMK_TEST_PARAMS(FileTypeCategory.TEST_FILE),
        DOCKSTORE_CWL(FileTypeCategory.GENERIC_DESCRIPTOR), CWL_TEST_JSON(FileTypeCategory.TEST_FILE),
        DOCKSTORE_WDL(FileTypeCategory.GENERIC_DESCRIPTOR), WDL_TEST_JSON(FileTypeCategory.TEST_FILE),
        DOCKERFILE(FileTypeCategory.CONTAINERFILE),
        NEXTFLOW(FileTypeCategory.GENERIC_DESCRIPTOR), NEXTFLOW_CONFIG(FileTypeCategory.PRIMARY_DESCRIPTOR), NEXTFLOW_TEST_PARAMS(FileTypeCategory.TEST_FILE),
        DOCKSTORE_YML(FileTypeCategory.OTHER),
        DOCKSTORE_SERVICE_YML(FileTypeCategory.PRIMARY_DESCRIPTOR), DOCKSTORE_SERVICE_TEST_JSON(FileTypeCategory.TEST_FILE), DOCKSTORE_SERVICE_OTHER(FileTypeCategory.OTHER),
        DOCKSTORE_GXFORMAT2(FileTypeCategory.GENERIC_DESCRIPTOR), GXFORMAT2_TEST_FILE(FileTypeCategory.TEST_FILE),
        DOCKSTORE_SWL(FileTypeCategory.GENERIC_DESCRIPTOR), SWL_TEST_JSON(FileTypeCategory.TEST_FILE),
        DOCKSTORE_JUPYTER(FileTypeCategory.PRIMARY_DESCRIPTOR), DOCKSTORE_NOTEBOOK_REES(FileTypeCategory.CONFIGURATION_FILE), DOCKSTORE_NOTEBOOK_DEVCONTAINER(FileTypeCategory.CONFIGURATION_FILE), DOCKSTORE_NOTEBOOK_TEST_FILE(FileTypeCategory.TEST_FILE), DOCKSTORE_NOTEBOOK_OTHER(FileTypeCategory.OTHER);
        // DOCKSTORE-2428 - demo how to add new workflow language

        private final FileTypeCategory category;

        FileType(FileTypeCategory category) {
            this.category = category;
        }

        public FileTypeCategory getCategory() {
            return category;
        }
    }

    public static String getDefaultDescriptorPath(DescriptorLanguage descriptorLanguage) {
        switch (descriptorLanguage) {
        case SMK:
            return "/Snakefile";
        case CWL:
            return "/Dockstore.cwl";
        case WDL:
            return "/Dockstore.wdl";
        case NEXTFLOW:
            return "/nextflow.config";
        case GXFORMAT2:
            return "/workflow-name.yml";
        default:
            return null;
        }
    }

    /**
     * Expanded version for API list of descriptor language
     */
    public static class DescriptorLanguageBean {

        public final String value;

        public final String friendlyName;

        public DescriptorLanguageBean(DescriptorLanguage descriptorLanguage) {
            this.value = descriptorLanguage.toString();
            this.friendlyName = descriptorLanguage.getFriendlyName();
        }
    }
}
