/*
 *    Copyright 2022 OICR
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
package io.openapi.api.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.DescriptorLanguage.FileType;
import io.dockstore.common.Registry;
import io.dockstore.common.SourceControl;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.BioWorkflow;
import io.dockstore.webservice.core.Image;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.WorkflowVersion;
import io.openapi.model.DescriptorType;
import io.openapi.model.ImageData;
import io.openapi.model.ToolFile;
import io.swagger.api.impl.ToolsImplCommon;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * @author wshands
 * @since 07/06/22
 */
class ToolsImplCommonTest {
    private static final String PLACEHOLDER_CONTENT = "potato";
    private static DockstoreWebserviceConfiguration actualConfig = new DockstoreWebserviceConfiguration();

    @BeforeAll
    public static void setup() {
        actualConfig.getExternalConfig().setHostname("localhost");
        actualConfig.getExternalConfig().setPort("8080");
        actualConfig.getExternalConfig().setScheme("http");
    }

    /**
     * Gets a fake source file to use for testing.  Only a few parameters
     *
     * @param verifiedSource If verified, what the verified source is
     * @param isService      Whether the entry that's going to add this file is a service or not
     * @param path           The path of the source file, must be unique if added to the same version
     * @return SourceFile    gets a fake source file to use for testing
     */
    private SourceFile getFakeSourceFile(String verifiedSource, boolean isService, String path) {
        SourceFile sourceFile = new SourceFile();
        sourceFile.setId(461402);
        sourceFile.setContent(PLACEHOLDER_CONTENT);
        sourceFile.setPath(path);
        sourceFile.setAbsolutePath(path);
        if (verifiedSource != null) {
            SourceFile.VerificationInformation verificationInformation1 = new SourceFile.VerificationInformation();
            verificationInformation1.verified = true;
            verificationInformation1.metadata = verifiedSource;
            verificationInformation1.platformVersion = "1.7.0";
            Map<String, SourceFile.VerificationInformation> verificationInformationMap = new HashMap<>();
            verificationInformationMap.put("platform", verificationInformation1);
            sourceFile.setVerifiedBySource(verificationInformationMap);
        }
        if (isService) {
            sourceFile.setType(DescriptorLanguage.FileType.DOCKSTORE_SERVICE_YML);
        } else {
            sourceFile.setType(DescriptorLanguage.FileType.DOCKSTORE_WDL);
        }
        return sourceFile;
    }

    @Test
    void testGalaxyConversion() {
        Workflow workflow = new BioWorkflow();
        workflow.setSourceControl(SourceControl.GITHUB);
        workflow.setRepository("fakeRepository");
        workflow.setOrganization("fakeOrganization");
        WorkflowVersion workflowVersion = new WorkflowVersion();
        SourceFile galaxyDescriptor = getFakeSourceFile(null, false, "/Galaxy.ga");
        galaxyDescriptor.setType(DescriptorLanguage.FileType.DOCKSTORE_GXFORMAT2);
        SourceFile dockstoreYML = getFakeSourceFile(null, false, "/.dockstore.yml");
        dockstoreYML.setType(DescriptorLanguage.FileType.DOCKSTORE_YML);
        Set<SourceFile> sourceFiles = new HashSet<>();
        sourceFiles.add(galaxyDescriptor);
        sourceFiles.add(dockstoreYML);
        workflowVersion.addSourceFile(dockstoreYML);
        workflowVersion.addSourceFile(galaxyDescriptor);
        workflowVersion.setWorkflowPath("/Galaxy.ga");
        List<String> workflowPaths = new ArrayList<>();
        workflowPaths.add("/Galaxy.ga");
        workflowVersion.setName("fakeName");
        workflow.addWorkflowVersion(workflowVersion);
        List<ToolFile> toolFiles = ToolsApiServiceImpl.getToolFiles(sourceFiles, workflowPaths, DescriptorType.GALAXY.toString(), "");
        assertEquals(2, toolFiles.size());
    }

    /**
     * This tests if the Dockstore Workflow can be properly converted to a GA4GH Tool
     * The workflow has 3 WorkflowVersions
     * One workflow version is hidden and not verified
     * The other two versions are verified and not hidden
     * Those two versions are verified by separate groups
     * This tests all properties including the verified and verified sources but not meta-version.
     * Tests a workflow with/without a workflowname
     */

    @Test
    void processImageDataForToolVersionTest() {
        io.dockstore.webservice.core.Tool tool = new io.dockstore.webservice.core.Tool();
        Tag tag = new Tag();
        Image image = new Image(new ArrayList<>(), "dummy", "dummy", "a", Registry.QUAY_IO, 1L, "now");
        Image image2 = new Image(new ArrayList<>(), "dummy", "dummy", "b", Registry.QUAY_IO, 2L, "now");
        Set<Image> images = new HashSet<>();
        images.add(image);
        images.add(image2);
        tag.setImages(images);
        tool.addWorkflowVersion(tag);
        io.openapi.model.ToolVersion toolVersion = new io.openapi.model.ToolVersion();
        toolVersion.setImages(new ArrayList<>());
        ToolsImplCommon.processImageDataForToolVersion(tool, tag, toolVersion);
        assertEquals(2, toolVersion.getImages().size(), "There should be the same amount of images as the Tag");
        List<Long> sortedSizes = toolVersion.getImages().stream().map(ImageData::getSize).sorted().toList();
        assertEquals(Long.valueOf(1L), sortedSizes.get(0));
        assertEquals(Long.valueOf(2L), sortedSizes.get(1));
        toolVersion = new io.openapi.model.ToolVersion();
        tag.setImages(new HashSet<>());
        tool = new io.dockstore.webservice.core.Tool();
        tool.addWorkflowVersion(tag);
        toolVersion.setImages(new ArrayList<>());
        ToolsImplCommon.processImageDataForToolVersion(tool, tag, toolVersion);
        assertEquals(1, toolVersion.getImages().size(), "There should be one default image when the Tag has none");
        assertEquals(Long.valueOf(0L), toolVersion.getImages().get(0).getSize());
    }

    @Test
    void getDescriptorTypeFromFileTypeTest() {
        assertEquals(DescriptorType.CWL, ToolsImplCommon.getDescriptorTypeFromFileType(FileType.DOCKSTORE_CWL).get());
        assertEquals(DescriptorType.CWL, ToolsImplCommon.getDescriptorTypeFromFileType(FileType.CWL_TEST_JSON).get());
        assertEquals(DescriptorType.GALAXY, ToolsImplCommon.getDescriptorTypeFromFileType(FileType.DOCKSTORE_GXFORMAT2).get());
        assertEquals(DescriptorType.GALAXY, ToolsImplCommon.getDescriptorTypeFromFileType(FileType.GXFORMAT2_TEST_FILE).get());
        assertEquals(DescriptorType.SERVICE, ToolsImplCommon.getDescriptorTypeFromFileType(FileType.DOCKSTORE_SERVICE_OTHER).get());
        assertEquals(DescriptorType.NFL, ToolsImplCommon.getDescriptorTypeFromFileType(FileType.NEXTFLOW_CONFIG).get());
        assertEquals(DescriptorType.NFL, ToolsImplCommon.getDescriptorTypeFromFileType(FileType.NEXTFLOW_TEST_PARAMS).get());
        assertEquals(DescriptorType.NFL, ToolsImplCommon.getDescriptorTypeFromFileType(FileType.NEXTFLOW).get());
    }

    @Test
    void getDescriptorTypeFromDescriptorLanguageTest() {
        assertEquals(DescriptorType.SMK, ToolsImplCommon.getDescriptorTypeFromDescriptorLanguage(DescriptorLanguage.SMK).get());
        assertEquals(DescriptorType.CWL, ToolsImplCommon.getDescriptorTypeFromDescriptorLanguage(DescriptorLanguage.CWL).get());
        assertEquals(DescriptorType.GALAXY, ToolsImplCommon.getDescriptorTypeFromDescriptorLanguage(DescriptorLanguage.GXFORMAT2).get());
        assertEquals(DescriptorType.NFL, ToolsImplCommon.getDescriptorTypeFromDescriptorLanguage(DescriptorLanguage.NEXTFLOW).get());
    }
}
