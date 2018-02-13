package io.swagger.api.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.common.collect.Table;
import com.google.gson.Gson;
import io.dockstore.common.Registry;
import io.dockstore.common.SourceControl;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.ToolMode;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.WorkflowMode;
import io.dockstore.webservice.core.WorkflowVersion;
import io.swagger.model.Tool;
import io.swagger.model.ToolDescriptor;
import io.swagger.model.ToolTests;
import io.swagger.model.ToolVersion;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author gluu
 * @since 17/01/18
 */
public class ToolsImplCommonTest {
    static final Gson gson = new Gson();
    static final String PLACEHOLDER_CONTENT = "potato";
    static DockstoreWebserviceConfiguration  actualConfig = new DockstoreWebserviceConfiguration();

    @BeforeClass
    public static void setup() {
        actualConfig.setHostname("localhost");
        actualConfig.setPort("8080");
        actualConfig.setScheme("http");
    }

    @Test
    public void wdlSourceFileToToolDescriptor() throws Exception {
        SourceFile sourceFile = new SourceFile();
        sourceFile.setType(SourceFile.FileType.DOCKSTORE_WDL);
        sourceFile.setPath("/Dockstore.wdl");
        sourceFile.setContent(PLACEHOLDER_CONTENT);
        sourceFile.setId(9001);
        ToolDescriptor actualToolDescriptor = ToolsImplCommon.sourceFileToToolDescriptor(sourceFile);
        ToolDescriptor expectedToolDescriptor = new ToolDescriptor();
        expectedToolDescriptor.setType(ToolDescriptor.TypeEnum.WDL);
        expectedToolDescriptor.setUrl("/Dockstore.wdl");
        expectedToolDescriptor.setDescriptor(PLACEHOLDER_CONTENT);
        assertEquals(expectedToolDescriptor, actualToolDescriptor);
    }

    @Test
    public void cwlSourceFileToToolDescriptor() throws Exception {
        SourceFile sourceFile = new SourceFile();
        sourceFile.setType(SourceFile.FileType.DOCKSTORE_CWL);
        sourceFile.setPath("/Dockstore.cwl");
        sourceFile.setContent(PLACEHOLDER_CONTENT);
        sourceFile.setId(9001);
        ToolDescriptor actualToolDescriptor = ToolsImplCommon.sourceFileToToolDescriptor(sourceFile);
        ToolDescriptor expectedToolDescriptor = new ToolDescriptor();
        expectedToolDescriptor.setType(ToolDescriptor.TypeEnum.CWL);
        expectedToolDescriptor.setUrl("/Dockstore.cwl");
        expectedToolDescriptor.setDescriptor(PLACEHOLDER_CONTENT);
        assertEquals(expectedToolDescriptor, actualToolDescriptor);
    }

    /**
     * This tests if the Dockstore Tool can be properly converted to a GA4GH Tool
     * The Dockstore Tool has with 3 WorkflowVersions
     * One workflow version is hidden and not verified
     * This tests all properties including the verified and verified sources but not meta-version.
     * @throws Exception
     */
    @Test
    public void convertDockstoreToolToTool() throws Exception {
        io.dockstore.webservice.core.Tool tool = new io.dockstore.webservice.core.Tool();
        tool.setMode(ToolMode.AUTO_DETECT_QUAY_TAGS_AUTOMATED_BUILDS);
        tool.setName("test6");
        tool.setDefaultDockerfilePath("/Dockerfile");
        tool.setDefaultCwlPath("/Dockstore.cwl");
        tool.setPrivateAccess(false);
        tool.setToolname("potato");
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
        sourceFile.setType(SourceFile.FileType.DOCKERFILE);
        sourceFile.setContent("TEST DOCKERFILE");
        sourceFile.setPath("/Dockerfile");
        SourceFile sourceFile2 = new SourceFile();
        sourceFile2.setId(1);
        sourceFile2.setType(SourceFile.FileType.DOCKSTORE_CWL);
        sourceFile2.setContent("TEST CWL");
        sourceFile2.setPath("/Dockstore.cwl");
        tag.addSourceFile(sourceFile);
        tag.addSourceFile(sourceFile2);
        tool.addTag(tag);
        Tool expectedTool = new Tool();
        expectedTool.setUrl("http://localhost:8080/api/ga4gh/v2/tools/quay.io%2Ftest_org%2Ftest6%2Fpotato");
        expectedTool.setId("quay.io/test_org/test6/potato");
        expectedTool.setOrganization("test_org");
        expectedTool.setToolname("test6/potato");
        expectedTool.setToolclass(ToolClassesApiServiceImpl.getCommandLineToolClass());
        expectedTool.setDescription("");
        expectedTool.setAuthor("sampleAuthor");
        expectedTool.setMetaVersion(null);
        expectedTool.setContains(Collections.EMPTY_LIST);
        expectedTool.setVerified(false);
        expectedTool.setSigned(false);
        expectedTool.setVersions(Collections.EMPTY_LIST);
        expectedTool.setVerifiedSource("[]");
        ToolVersion expectedToolVersion = new ToolVersion();
        expectedToolVersion.setName("sampleTag");
        expectedToolVersion.setUrl("http://localhost:8080/api/ga4gh/v2/tools/quay.io%2Ftest_org%2Ftest6%2Fpotato/versions/sampleTag");
        expectedToolVersion.setId("quay.io/test_org/test6/potato:sampleTag");
        expectedToolVersion.setImage("sampleImageId");
        List<ToolVersion.DescriptorTypeEnum> descriptorTypeList = new ArrayList<>();
        descriptorTypeList.add(ToolVersion.DescriptorTypeEnum.CWL);
        expectedToolVersion.setDescriptorType(descriptorTypeList);
        expectedToolVersion.setDockerfile(true);
        expectedToolVersion.setMetaVersion(null);
        expectedToolVersion.setVerified(false);
        expectedToolVersion.setVerifiedSource("");
        List<ToolVersion> expectedToolVersions = new ArrayList<>();
        expectedToolVersions.add(expectedToolVersion);
        expectedTool.setVersions(expectedToolVersions);
        Pair<Tool, Table<String, SourceFile.FileType, Object>> toolTablePair = ToolsImplCommon.convertEntryToTool(tool, actualConfig);
        Tool actualTool = toolTablePair.getLeft();
        actualTool.setMetaVersion(null);
        actualTool.getVersions().parallelStream().forEach(version -> version.setMetaVersion(null));
        Assert.assertEquals(expectedTool, actualTool);

    }

    /**
     * This tests if the Dockstore Workflow can be properly converted to a GA4GH Tool
     * The workflow has 3 WorkflowVersions
     * One workflow version is hidden and not verified
     * The other two versions are verified and not hidden
     * Those two versions are verified by separate groups
     * This tests all properties including the verified and verified sources but not meta-version.
     * @throws Exception
     */
    @Test
    public void convertDockstoreWorkflowToTool() throws Exception {
        final String REFERENCE1 = "aaa";
        final String REFERENCE2 = "bbb";
        final String REFERENCE3 = "ccc";
        String json;
        SourceFile actualSourceFile1 = new SourceFile();
        actualSourceFile1.setId(461402);
        actualSourceFile1.setType(SourceFile.FileType.DOCKSTORE_WDL);
        actualSourceFile1.setContent(PLACEHOLDER_CONTENT);
        actualSourceFile1.setPath("/pcawg-cgp-somatic-workflow.wdl");
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
        actualWorkflowVersion1.setVerifiedSource(null);
        Workflow workflow = new Workflow();
        json = gson.toJson(actualWorkflowVersion1);
        WorkflowVersion actualWorkflowVersion2 = gson.fromJson(json, WorkflowVersion.class);
        actualWorkflowVersion2.setName(REFERENCE2);
        actualWorkflowVersion2.setReference(REFERENCE2);
        actualWorkflowVersion2.setHidden(false);
        actualWorkflowVersion2.setVerifiedSource("potatoTesterSource");
        actualWorkflowVersion2.setVerified(true);
        json = gson.toJson(actualWorkflowVersion2);
        WorkflowVersion actualWorkflowVersion3 = gson.fromJson(json, WorkflowVersion.class);
        actualWorkflowVersion3.setName(REFERENCE3);
        actualWorkflowVersion3.setReference(REFERENCE3);
        actualWorkflowVersion3.setVerifiedSource("chickenTesterSource");
        workflow.addWorkflowVersion(actualWorkflowVersion1);
        workflow.addWorkflowVersion(actualWorkflowVersion2);
        workflow.addWorkflowVersion(actualWorkflowVersion3);
        workflow.setMode(WorkflowMode.FULL);
        workflow.setWorkflowName(null);
        workflow.setOrganization("ICGC-TCGA-PanCancer");
        workflow.setRepository("wdl-pcawg-sanger-cgp-workflow");
        workflow.setSourceControl(SourceControl.GITHUB.toString());
        workflow.setDescriptorType("wdl");
        workflow.setDefaultWorkflowPath("/pcawg-cgp-somatic-workflow.wdl");
        workflow.setDefaultTestParameterFilePath(null);
        workflow.setId(950);
        workflow.setAuthor(null);
        workflow.setDescription(null);
        workflow.setLabels(Collections.emptySortedSet());
        workflow.setUsers(Collections.emptySet());
        workflow.setEmail(null);
        workflow.setDefaultVersion(REFERENCE2);
        workflow.setIsPublished(true);
        workflow.setLastModified(null);
        workflow.setLastUpdated(null);
        workflow.setGitUrl("git@github.com:ICGC-TCGA-PanCancer/wdl-pcawg-sanger-cgp-workflow.git");
        Pair<Tool, Table<String, SourceFile.FileType, Object>> toolTablePair = ToolsImplCommon.convertEntryToTool(workflow, actualConfig);
        Tool actualTool = toolTablePair.getLeft();
        ToolVersion expectedToolVersion1 = new ToolVersion();
        expectedToolVersion1.setName(REFERENCE2);
        expectedToolVersion1
                .setUrl("http://localhost:8080/api/ga4gh/v2/tools/%23workflow%2Fgithub.com%2FICGC-TCGA-PanCancer%2Fwdl-pcawg-sanger-cgp-workflow/versions/"+ REFERENCE2);
        expectedToolVersion1.setId("#workflow/github.com/ICGC-TCGA-PanCancer/wdl-pcawg-sanger-cgp-workflow:" + REFERENCE2);
        List<ToolVersion.DescriptorTypeEnum> descriptorTypeList = new ArrayList<>();
        descriptorTypeList.add(ToolVersion.DescriptorTypeEnum.WDL);
        expectedToolVersion1.setImage("");
        expectedToolVersion1.setDescriptorType(descriptorTypeList);
        expectedToolVersion1.setDockerfile(false);
        // Meta-version dates are currently dependant on the environment, disabling for now
        expectedToolVersion1.setMetaVersion(null);
        expectedToolVersion1.setVerified(true);
        expectedToolVersion1.setVerifiedSource("potatoTesterSource");
        json = gson.toJson(expectedToolVersion1);
        ToolVersion expectedToolVersion2 = gson.fromJson(json, ToolVersion.class);
        expectedToolVersion2.setName(REFERENCE3);
        expectedToolVersion2.setVerifiedSource("chickenTesterSource");
        expectedToolVersion2.setUrl("http://localhost:8080/api/ga4gh/v2/tools/%23workflow%2Fgithub.com%2FICGC-TCGA-PanCancer%2Fwdl-pcawg-sanger-cgp-workflow/versions/" + REFERENCE3);
        expectedToolVersion2.setId("#workflow/github.com/ICGC-TCGA-PanCancer/wdl-pcawg-sanger-cgp-workflow:"+ REFERENCE3);
        List<ToolVersion> expectedToolVersions = new ArrayList<>();
        expectedToolVersions.add(expectedToolVersion1);
        expectedToolVersions.add(expectedToolVersion2);
        Tool expectedTool = new Tool();
        expectedTool
                .setUrl("http://localhost:8080/api/ga4gh/v2/tools/%23workflow%2Fgithub.com%2FICGC-TCGA-PanCancer%2Fwdl-pcawg-sanger-cgp-workflow");
        expectedTool.setId("#workflow/github.com/ICGC-TCGA-PanCancer/wdl-pcawg-sanger-cgp-workflow");
        expectedTool.setOrganization("ICGC-TCGA-PanCancer");
        expectedTool.setToolname("wdl-pcawg-sanger-cgp-workflow");
        expectedTool.setToolclass(ToolClassesApiServiceImpl.getWorkflowClass());
        expectedTool.setDescription("");
        expectedTool.setAuthor("Unknown author");
        // Meta-version dates are currently dependant on the environment, disabling for now
        expectedTool.setMetaVersion(null);
        expectedTool.setContains(Collections.EMPTY_LIST);
        expectedTool.setVerified(true);
        expectedTool.setVerifiedSource("[\"chickenTesterSource\",\"potatoTesterSource\"]");
        expectedTool.setSigned(false);
        expectedTool.setVersions(expectedToolVersions);
        actualTool.setMetaVersion(null);

        // Meta-version dates are currently dependant on the environment, disabling for now
        List<ToolVersion> versions = actualTool.getVersions();
        versions.stream().forEach(version -> version.setMetaVersion(null));
        actualTool.setVersions(versions);
        Assert.assertEquals(expectedTool, actualTool);
    }

    @Test
    public void sourceFileToToolTests() throws Exception {
        SourceFile sourceFile = new SourceFile();
        sourceFile.setType(SourceFile.FileType.CWL_TEST_JSON);
        sourceFile.setPath("/test.cwl.json");
        sourceFile.setContent(PLACEHOLDER_CONTENT);
        sourceFile.setId(9001);
        ToolTests actualToolTests = ToolsImplCommon.sourceFileToToolTests(sourceFile);
        ToolTests expectedToolTests = new ToolTests();
        expectedToolTests.setTest(PLACEHOLDER_CONTENT);
        expectedToolTests.setUrl("/test.cwl.json");
        assertEquals(expectedToolTests, actualToolTests);
    }
}
