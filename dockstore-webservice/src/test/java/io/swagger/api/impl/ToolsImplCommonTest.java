package io.swagger.api.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.common.collect.Table;
import com.google.gson.Gson;
import io.dockstore.common.SourceControl;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.WorkflowMode;
import io.dockstore.webservice.core.WorkflowVersion;
import io.swagger.model.Tool;
import io.swagger.model.ToolDescriptor;
import io.swagger.model.ToolTests;
import io.swagger.model.ToolVersion;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author gluu
 * @since 17/01/18
 */
public class ToolsImplCommonTest {
    static final Gson gson = new Gson();
    static final String REFERENCE1 = "aaa";
    static final String REFERENCE2 = "bbb";
    static final String REFERENCE3 = "ccc";
    static String json;
    @Test
    public void sourceFileToToolDescriptor() throws Exception {
        SourceFile sourceFile = new SourceFile();
        sourceFile.setType(SourceFile.FileType.DOCKSTORE_WDL);
        sourceFile.setPath("/Dockstore.wdl");
        sourceFile.setContent("potato");
        sourceFile.setId(9001);
        ToolDescriptor actualToolDescriptor = ToolsImplCommon.sourceFileToToolDescriptor(sourceFile);
        ToolDescriptor expectedToolDescriptor = new ToolDescriptor();
        expectedToolDescriptor.setType(ToolDescriptor.TypeEnum.WDL);
        expectedToolDescriptor.setUrl("/Dockstore.wdl");
        expectedToolDescriptor.setDescriptor("potato");
        assertEquals(expectedToolDescriptor, actualToolDescriptor);
    }

    /**
     * This tests if the Dockstore Entry can be properly converted to a GA4GH Tool
     * The Dockstore Entry is actually a Dockstore Workflow with 3 WorkflowVersions
     * One workflow version is hidden and not verified
     * The other two versions are not hidden and verified
     * Those two versions are verified by separate groups
     * @throws Exception
     */
    @Test
    public void convertEntryToTool() throws Exception {
        SourceFile actualSourceFile1 = new SourceFile();
        actualSourceFile1.setId(461402);
        actualSourceFile1.setType(SourceFile.FileType.DOCKSTORE_WDL);
        actualSourceFile1.setContent("potato");
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
        workflow.setSourceControl(SourceControl.GITHUB);
        workflow.setPath("github.com/ICGC-TCGA-PanCancer/wdl-pcawg-sanger-cgp-workflow");
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
        DockstoreWebserviceConfiguration actualConfig = new DockstoreWebserviceConfiguration();
        actualConfig.setHostname("localhost");
        actualConfig.setPort("8080");
        actualConfig.setScheme("http");
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
        sourceFile.setContent("potato");
        sourceFile.setId(9001);
        ToolTests actualToolTests = ToolsImplCommon.sourceFileToToolTests(sourceFile);
        ToolTests expectedToolTests = new ToolTests();
        expectedToolTests.setTest("potato");
        expectedToolTests.setUrl("/test.cwl.json");
        assertEquals(expectedToolTests, actualToolTests);
    }
}
