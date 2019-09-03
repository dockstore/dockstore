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
package io.swagger.api.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.Registry;
import io.dockstore.common.SourceControl;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.BioWorkflow;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.ToolMode;
import io.dockstore.webservice.core.WorkflowMode;
import io.dockstore.webservice.core.WorkflowVersion;
import io.swagger.model.DescriptorType;
import io.swagger.model.ExtendedFileWrapper;
import io.swagger.model.FileWrapper;
import io.swagger.model.Tool;
import io.swagger.model.ToolVersion;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author gluu
 * @since 17/01/18
 */
public class ToolsImplCommonTest {
    private static final String PLACEHOLDER_CONTENT = "potato";
    private static DockstoreWebserviceConfiguration  actualConfig = new DockstoreWebserviceConfiguration();

    @BeforeClass
    public static void setup() {
        actualConfig.getExternalConfig().setHostname("localhost");
        actualConfig.getExternalConfig().setPort("8080");
        actualConfig.getExternalConfig().setScheme("http");
    }

    @Test
    public void wdlSourceFileToToolDescriptor() {
        SourceFile sourceFile = new SourceFile();
        sourceFile.setType(DescriptorLanguage.FileType.DOCKSTORE_WDL);
        sourceFile.setPath("/Dockstore.wdl");
        sourceFile.setAbsolutePath("/Dockstore.wdl");
        sourceFile.setContent(PLACEHOLDER_CONTENT);
        sourceFile.setId(9001);
        FileWrapper actualToolDescriptor = ToolsImplCommon.sourceFileToToolDescriptor("/Dockstore.wdl",sourceFile);
        FileWrapper expectedToolDescriptor = new FileWrapper();
        expectedToolDescriptor.setUrl("/Dockstore.wdl");
        expectedToolDescriptor.setContent(PLACEHOLDER_CONTENT);
        assertEquals(actualToolDescriptor, expectedToolDescriptor);
    }

    @Test
    public void cwlSourceFileToToolDescriptor() {
        SourceFile sourceFile = new SourceFile();
        sourceFile.setType(DescriptorLanguage.FileType.DOCKSTORE_CWL);
        sourceFile.setPath("/Dockstore.cwl");
        sourceFile.setAbsolutePath("/Dockstore.cwl");
        sourceFile.setContent(PLACEHOLDER_CONTENT);
        sourceFile.setId(9001);
        FileWrapper actualToolDescriptor = ToolsImplCommon.sourceFileToToolDescriptor("/Dockstore.cwl",sourceFile);
        FileWrapper expectedToolDescriptor = new FileWrapper();
        expectedToolDescriptor.setUrl("/Dockstore.cwl");
        expectedToolDescriptor.setContent(PLACEHOLDER_CONTENT);
        assertEquals(actualToolDescriptor, expectedToolDescriptor);
    }

    /**
     * This tests if the Dockstore Tool can be properly converted to a GA4GH Tool
     * The Dockstore Tool has with 3 WorkflowVersions
     * One workflow version is hidden and not verified
     * This tests all properties including the verified and verified sources but not meta-version.
     * Tests a tool with/without a toolname
     */
    @Test
    public void convertDockstoreToolToTool() {
        convertDockstoreToolToTool("potato");
        convertDockstoreToolToTool(null);
    }

    private void convertDockstoreToolToTool(String toolname) {
        io.dockstore.webservice.core.Tool tool = new io.dockstore.webservice.core.Tool();
        tool.setMode(ToolMode.AUTO_DETECT_QUAY_TAGS_AUTOMATED_BUILDS);
        tool.setName("test6");
        tool.setDefaultDockerfilePath("/Dockerfile");
        tool.setDefaultCwlPath("/Dockstore.cwl");
        tool.setPrivateAccess(false);
        tool.setToolname(toolname);
        tool.setNamespace("test_org");
        tool.setRegistry(Registry.QUAY_IO.toString());
        tool.setAuthor("sampleAuthor");
        tool.setGitUrl("git@github.com:test_org/test6.git");

        Tag tag = new Tag();
        tag.setImageId("sampleImageId");
        tag.setName("sampleTag");
        tag.setSize(0);
        tag.setDockerfilePath("/Dockerfile");
        tag.setCwlPath("/Dockstore.cwl");
        tag.setAutomated(true);
        tag.setReference("sampleReference");
        tag.setValid(true);
        SourceFile sourceFile = new SourceFile();
        sourceFile.setId(0);
        sourceFile.setType(DescriptorLanguage.FileType.DOCKERFILE);
        sourceFile.setContent("TEST DOCKERFILE");
        sourceFile.setPath("/Dockerfile");
        sourceFile.setAbsolutePath("/Dockerfile");
        SourceFile sourceFile2 = new SourceFile();
        sourceFile2.setId(1);
        sourceFile2.setType(DescriptorLanguage.FileType.DOCKSTORE_CWL);
        sourceFile2.setContent("TEST CWL");
        sourceFile2.setPath("/Dockstore.cwl");
        sourceFile2.setAbsolutePath("/Dockstore.cwl");
        tag.addSourceFile(sourceFile);
        tag.addSourceFile(sourceFile2);
        tag.updateVerified();
        tool.addWorkflowVersion(tag);
        tool.setCheckerWorkflow(null);
        Tool expectedTool = new Tool();
        if (toolname != null) {
            expectedTool.setUrl("http://localhost:8080/api/ga4gh/v2/tools/quay.io%2Ftest_org%2Ftest6%2Fpotato");
            expectedTool.setId("quay.io/test_org/test6/potato");
            expectedTool.setToolname("test6/potato");

        } else {
            expectedTool.setUrl("http://localhost:8080/api/ga4gh/v2/tools/quay.io%2Ftest_org%2Ftest6");
            expectedTool.setId("quay.io/test_org/test6");
            expectedTool.setToolname("test6");
        }
        expectedTool.setCheckerUrl("");
        expectedTool.setHasChecker(false);
        expectedTool.setOrganization("test_org");
        expectedTool.setToolclass(ToolClassesApiServiceImpl.getCommandLineToolClass());
        expectedTool.setDescription("");
        expectedTool.setAuthor("sampleAuthor");
        expectedTool.setMetaVersion(null);
        expectedTool.setAliases(Collections.EMPTY_LIST);
        expectedTool.setContains(Collections.EMPTY_LIST);
        expectedTool.setVerified(false);
        expectedTool.setSigned(false);
        expectedTool.setVersions(Collections.EMPTY_LIST);
        expectedTool.setVerifiedSource(null);
        ToolVersion expectedToolVersion = new ToolVersion();
        expectedToolVersion.setName("sampleTag");
        if (toolname != null) {
            expectedToolVersion.setUrl("http://localhost:8080/api/ga4gh/v2/tools/quay.io%2Ftest_org%2Ftest6%2Fpotato/versions/sampleTag");
            expectedToolVersion.setId("quay.io/test_org/test6/potato:sampleTag");
        } else {
            expectedToolVersion.setUrl("http://localhost:8080/api/ga4gh/v2/tools/quay.io%2Ftest_org%2Ftest6/versions/sampleTag");
            expectedToolVersion.setId("quay.io/test_org/test6:sampleTag");
        }
        expectedToolVersion.setImage("sampleImageId");
        List<DescriptorType> descriptorTypeList = new ArrayList<>();
        descriptorTypeList.add(DescriptorType.CWL);
        expectedToolVersion.setDescriptorType(descriptorTypeList);
        expectedToolVersion.setContainerfile(true);
        expectedToolVersion.setMetaVersion(null);
        expectedToolVersion.setVerified(false);
        expectedToolVersion.setVerifiedSource(null);
        expectedToolVersion.setRegistryUrl("quay.io");
        expectedToolVersion.setImageName("quay.io/test_org/test6");
        List<ToolVersion> expectedToolVersions = new ArrayList<>();
        expectedToolVersions.add(expectedToolVersion);
        expectedTool.setVersions(expectedToolVersions);
        Tool actualTool = ToolsImplCommon.convertEntryToTool(tool, actualConfig);
        actualTool.setMetaVersion(null);
        actualTool.getVersions().parallelStream().forEach(version -> version.setMetaVersion(null));
        assertEquals(expectedTool, actualTool);

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
    public void convertDockstoreWorkflowToTool() throws IOException {
        convertDockstoreWorkflowToTool("potato", false);
        convertDockstoreWorkflowToTool(null, false);

        convertDockstoreWorkflowToTool("potato", true);
        convertDockstoreWorkflowToTool(null, true);
    }

    /**
     * Gets a fake source file to use for testing.  Only a few parameters
     * @param verifiedSource    If verified, what the verified source is
     * @param isService         Whether the entry that's going to add this file is a service or not
     * @param path              The path of the sourcefile, must be unique if added to the same version
     * @return
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
            verificationInformation1.metadata = "Dockstore team";
            verificationInformation1.platformVersion = "1.7.0";
            Map<String, SourceFile.VerificationInformation> verificationInformationMap = new HashMap<>();
            verificationInformationMap.put(verifiedSource, verificationInformation1);
            sourceFile.setVerifiedBySource(verificationInformationMap);
        }
        if (isService) {
            sourceFile.setType(DescriptorLanguage.FileType.DOCKSTORE_SERVICE_YML);
        } else {
            sourceFile.setType(DescriptorLanguage.FileType.DOCKSTORE_WDL);
        }
        return sourceFile;
    }

    private void convertDockstoreWorkflowToTool(String toolname, boolean isService) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        final String TOOLNAME = toolname;
        final String REFERENCE1 = "aaa";
        final String REFERENCE2 = "bbb";
        final String REFERENCE3 = "ccc";
        String json;
        SourceFile actualSourceFile1 = getFakeSourceFile(null, isService, "/pcawg-cgp-somatic-workflow.wdl");
        WorkflowVersion actualWorkflowVersion1 = new WorkflowVersion();
        actualWorkflowVersion1.setWorkflowPath("/pcawg-cgp-somatic-workflow.wdl");
        actualWorkflowVersion1.setLastModified(null);
        actualWorkflowVersion1.setReference(REFERENCE1);
        actualWorkflowVersion1.addSourceFile(actualSourceFile1);
        actualWorkflowVersion1.setHidden(true);
        actualWorkflowVersion1.setValid(true);
        actualWorkflowVersion1.setName(REFERENCE1);
        actualWorkflowVersion1.setDirtyBit(false);
        actualWorkflowVersion1.setValid(false);
        BioWorkflow workflow = new BioWorkflow();
        json = mapper.writeValueAsString(actualWorkflowVersion1);
        WorkflowVersion actualWorkflowVersion2 = mapper.readValue(json, WorkflowVersion.class);
        actualWorkflowVersion2.setName(REFERENCE2);
        actualWorkflowVersion2.setReference(REFERENCE2);
        actualWorkflowVersion2.setHidden(false);
        SourceFile sourceFile2 = getFakeSourceFile("chickenTesterSource", isService, "/pcawg-cgp-somatic-workflow2.wdl");
        actualWorkflowVersion2.addSourceFile(sourceFile2);
        json = mapper.writeValueAsString(actualWorkflowVersion2);
        WorkflowVersion actualWorkflowVersion3 = mapper.readValue(json, WorkflowVersion.class);
        actualWorkflowVersion3.setName(REFERENCE3);
        actualWorkflowVersion3.setReference(REFERENCE3);
        SourceFile sourceFile3 = getFakeSourceFile("potatoTesterSource", isService, "/pcawg-cgp-somatic-workflow.wdl3");
        actualWorkflowVersion3.addSourceFile(sourceFile3);
        actualWorkflowVersion1.updateVerified();
        actualWorkflowVersion2.updateVerified();
        actualWorkflowVersion3.updateVerified();
        // Check that Dockstore version is actually has the right verified source
        Assert.assertEquals("[\"chickenTesterSource\",\"potatoTesterSource\"]", actualWorkflowVersion3.getVerifiedSource());
        workflow.addWorkflowVersion(actualWorkflowVersion1);
        workflow.addWorkflowVersion(actualWorkflowVersion2);
        workflow.addWorkflowVersion(actualWorkflowVersion3);
        workflow.setMode(WorkflowMode.FULL);
        workflow.setWorkflowName(toolname);
        workflow.setOrganization("ICGC-TCGA-PanCancer");
        workflow.setRepository("wdl-pcawg-sanger-cgp-workflow");
        workflow.setSourceControl(SourceControl.GITHUB);
        if (isService) {
            workflow.setDescriptorType(DescriptorLanguage.SERVICE);
        } else {
            workflow.setDescriptorType(DescriptorLanguage.WDL);
        }
        workflow.setDefaultWorkflowPath("/pcawg-cgp-somatic-workflow.wdl");
        workflow.setDefaultTestParameterFilePath(null);
        workflow.setId(950);
        workflow.setAuthor(null);
        workflow.setDescription(null);
        workflow.setLabels(Collections.emptySortedSet());
        workflow.setUsers(Collections.emptySortedSet());
        workflow.setEmail(null);
        workflow.setDefaultVersion(REFERENCE2);
        workflow.setIsPublished(true);
        workflow.setLastModified(null);
        workflow.setLastUpdated(null);
        workflow.setGitUrl("git@github.com:ICGC-TCGA-PanCancer/wdl-pcawg-sanger-cgp-workflow.git");
        workflow.setCheckerWorkflow(workflow);
        Tool actualTool = ToolsImplCommon.convertEntryToTool(workflow, actualConfig);
        ToolVersion expectedToolVersion1 = new ToolVersion();
        expectedToolVersion1.setName(REFERENCE2);
        if (toolname != null) {

            if (isService) {
                expectedToolVersion1.setId("#service/github.com/ICGC-TCGA-PanCancer/wdl-pcawg-sanger-cgp-workflow/"+ TOOLNAME + ":" + REFERENCE2);
                expectedToolVersion1.setUrl("http://localhost:8080/api/ga4gh/v2/tools/%23service%2Fgithub.com%2FICGC-TCGA-PanCancer%2Fwdl-pcawg-sanger-cgp-workflow%2F"
                        + TOOLNAME + "/versions/" + REFERENCE2);
            } else {
                expectedToolVersion1.setId("#workflow/github.com/ICGC-TCGA-PanCancer/wdl-pcawg-sanger-cgp-workflow/"+ TOOLNAME + ":" + REFERENCE2);
                expectedToolVersion1.setUrl("http://localhost:8080/api/ga4gh/v2/tools/%23workflow%2Fgithub.com%2FICGC-TCGA-PanCancer%2Fwdl-pcawg-sanger-cgp-workflow%2F"
                        + TOOLNAME + "/versions/" + REFERENCE2);
            }
        } else {

            if (isService) {
                expectedToolVersion1.setId("#service/github.com/ICGC-TCGA-PanCancer/wdl-pcawg-sanger-cgp-workflow:" + REFERENCE2);
                expectedToolVersion1.setUrl("http://localhost:8080/api/ga4gh/v2/tools/%23service%2Fgithub.com%2FICGC-TCGA-PanCancer%2Fwdl-pcawg-sanger-cgp-workflow/versions/" + REFERENCE2);
            } else {
                expectedToolVersion1.setUrl("http://localhost:8080/api/ga4gh/v2/tools/%23workflow%2Fgithub.com%2FICGC-TCGA-PanCancer%2Fwdl-pcawg-sanger-cgp-workflow/versions/" + REFERENCE2);
                expectedToolVersion1.setId("#workflow/github.com/ICGC-TCGA-PanCancer/wdl-pcawg-sanger-cgp-workflow:" + REFERENCE2);
            }
        }

        List<DescriptorType> descriptorTypeList = new ArrayList<>();
        if (isService) {
            descriptorTypeList.add(DescriptorType.SERVICE);
        } else {
            descriptorTypeList.add(DescriptorType.WDL);
        }
        expectedToolVersion1.setImage("");
        expectedToolVersion1.setDescriptorType(descriptorTypeList);
        expectedToolVersion1.setContainerfile(false);
        // Meta-version dates are currently dependant on the environment, disabling for now
        expectedToolVersion1.setMetaVersion(null);
        expectedToolVersion1.setVerified(true);
        expectedToolVersion1.setVerifiedSource("[\"chickenTesterSource\"]");
        expectedToolVersion1.setImageName("");
        expectedToolVersion1.setRegistryUrl("");
        json = mapper.writeValueAsString(expectedToolVersion1);
        ToolVersion expectedToolVersion2 = mapper.readValue(json, ToolVersion.class);
        expectedToolVersion2.setName(REFERENCE3);
        expectedToolVersion2.setVerifiedSource("[\"chickenTesterSource\",\"potatoTesterSource\"]");
        expectedToolVersion2.setImageName("");
        expectedToolVersion2.setRegistryUrl("");

        if (toolname != null) {
            if (isService) {
                expectedToolVersion2.setId("#service/github.com/ICGC-TCGA-PanCancer/wdl-pcawg-sanger-cgp-workflow/" + TOOLNAME + ":" + REFERENCE3);
                expectedToolVersion2.setUrl("http://localhost:8080/api/ga4gh/v2/tools/%23service%2Fgithub.com%2FICGC-TCGA-PanCancer%2Fwdl-pcawg-sanger-cgp-workflow%2F"
                        + TOOLNAME + "/versions/" + REFERENCE3);
            } else {
                expectedToolVersion2.setId("#workflow/github.com/ICGC-TCGA-PanCancer/wdl-pcawg-sanger-cgp-workflow/" + TOOLNAME + ":" + REFERENCE3);
                expectedToolVersion2.setUrl("http://localhost:8080/api/ga4gh/v2/tools/%23workflow%2Fgithub.com%2FICGC-TCGA-PanCancer%2Fwdl-pcawg-sanger-cgp-workflow%2F"
                        + TOOLNAME + "/versions/" + REFERENCE3);
            }
        } else {

            if (isService) {
                expectedToolVersion2.setId("#service/github.com/ICGC-TCGA-PanCancer/wdl-pcawg-sanger-cgp-workflow:" + REFERENCE3);
                expectedToolVersion2.setUrl("http://localhost:8080/api/ga4gh/v2/tools/%23service%2Fgithub.com%2FICGC-TCGA-PanCancer%2Fwdl-pcawg-sanger-cgp-workflow/versions/" + REFERENCE3);
            } else {
                expectedToolVersion2.setId("#workflow/github.com/ICGC-TCGA-PanCancer/wdl-pcawg-sanger-cgp-workflow:" + REFERENCE3);
                expectedToolVersion2.setUrl("http://localhost:8080/api/ga4gh/v2/tools/%23workflow%2Fgithub.com%2FICGC-TCGA-PanCancer%2Fwdl-pcawg-sanger-cgp-workflow/versions/" + REFERENCE3);
            }
        }
        List<ToolVersion> expectedToolVersions = new ArrayList<>();
        expectedToolVersions.add(expectedToolVersion1);
        expectedToolVersions.add(expectedToolVersion2);
        Tool expectedTool = new Tool();

        expectedTool.setOrganization("ICGC-TCGA-PanCancer");
        if (toolname != null) {

            if (isService) {
                expectedTool.setId("#service/github.com/ICGC-TCGA-PanCancer/wdl-pcawg-sanger-cgp-workflow/"+ TOOLNAME);
                expectedTool
                        .setUrl("http://localhost:8080/api/ga4gh/v2/tools/%23service%2Fgithub.com%2FICGC-TCGA-PanCancer%2Fwdl-pcawg-sanger-cgp-workflow%2F"+ TOOLNAME);
            } else {
                expectedTool.setId("#workflow/github.com/ICGC-TCGA-PanCancer/wdl-pcawg-sanger-cgp-workflow/"+ TOOLNAME);
                expectedTool
                        .setUrl("http://localhost:8080/api/ga4gh/v2/tools/%23workflow%2Fgithub.com%2FICGC-TCGA-PanCancer%2Fwdl-pcawg-sanger-cgp-workflow%2F"+ TOOLNAME);
            }
            expectedTool.setToolname("wdl-pcawg-sanger-cgp-workflow/" + TOOLNAME);
            expectedTool.setCheckerUrl("http://localhost:8080/api/ga4gh/v2/tools/%23workflow%2Fgithub.com%2FICGC-TCGA-PanCancer%2Fwdl-pcawg-sanger-cgp-workflow%2F"+ TOOLNAME);
        } else {

            if (isService) {
                expectedTool
                        .setUrl("http://localhost:8080/api/ga4gh/v2/tools/%23service%2Fgithub.com%2FICGC-TCGA-PanCancer%2Fwdl-pcawg-sanger-cgp-workflow");
                expectedTool.setId("#service/github.com/ICGC-TCGA-PanCancer/wdl-pcawg-sanger-cgp-workflow");
            } else {
                expectedTool
                        .setUrl("http://localhost:8080/api/ga4gh/v2/tools/%23workflow%2Fgithub.com%2FICGC-TCGA-PanCancer%2Fwdl-pcawg-sanger-cgp-workflow");
                expectedTool.setId("#workflow/github.com/ICGC-TCGA-PanCancer/wdl-pcawg-sanger-cgp-workflow");
            }
            expectedTool.setToolname("wdl-pcawg-sanger-cgp-workflow");
            expectedTool.setCheckerUrl("http://localhost:8080/api/ga4gh/v2/tools/%23workflow%2Fgithub.com%2FICGC-TCGA-PanCancer%2Fwdl-pcawg-sanger-cgp-workflow");
        }
        expectedTool.setHasChecker(true);
        expectedTool.setToolclass(ToolClassesApiServiceImpl.getWorkflowClass());
        expectedTool.setDescription("");
        expectedTool.setAuthor("Unknown author");
        // Meta-version dates are currently dependant on the environment, disabling for now
        expectedTool.setMetaVersion(null);
        expectedTool.setContains(Collections.EMPTY_LIST);
        expectedTool.setAliases(Collections.EMPTY_LIST);
        expectedTool.setVerified(true);
        expectedTool.setVerifiedSource("[\"chickenTesterSource\",\"potatoTesterSource\"]");
        expectedTool.setSigned(false);
        expectedTool.setVersions(expectedToolVersions);
        actualTool.setMetaVersion(null);

        // Meta-version dates are currently dependant on the environment, disabling for now
        List<ToolVersion> versions = actualTool.getVersions();
        versions.forEach(version -> version.setMetaVersion(null));
        actualTool.setVersions(versions);
        assertEquals(expectedTool, actualTool);
    }

    @Test
    public void sourceFileToToolTests() {
        SourceFile sourceFile = new SourceFile();
        sourceFile.setType(DescriptorLanguage.FileType.CWL_TEST_JSON);
        sourceFile.setPath("/test.cwl.json");
        sourceFile.setAbsolutePath("/test.cwl.json");
        sourceFile.setContent(PLACEHOLDER_CONTENT);
        sourceFile.setId(9001);
        FileWrapper actualToolTests = ToolsImplCommon.sourceFileToToolTests("", sourceFile);
        ExtendedFileWrapper expectedToolTests = new ExtendedFileWrapper();
        expectedToolTests.setContent(PLACEHOLDER_CONTENT);
        expectedToolTests.setUrl("/test.cwl.json");
        assertEquals(expectedToolTests, actualToolTests);
    }
}
