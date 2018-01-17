package io.swagger.api.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.common.collect.Table;
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
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author gluu
 * @since 17/01/18
 */
public class ToolsImplCommonTest {
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
     *
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
        actualWorkflowVersion1.setReference("ccc");
        actualWorkflowVersion1.addSourceFile(actualSourceFile1);
        actualWorkflowVersion1.setHidden(true);
        actualWorkflowVersion1.setValid(true);
        actualWorkflowVersion1.setName("ccc");
        actualWorkflowVersion1.setDirtyBit(false);
        actualWorkflowVersion1.setValid(false);
        actualWorkflowVersion1.setVerifiedSource(null);
        WorkflowVersion actualWorkflowVersion2 = actualWorkflowVersion1;
        actualWorkflowVersion2.setName("master");
        actualWorkflowVersion2.setReference("master");
        actualWorkflowVersion2.setHidden(false);
        Workflow workflow = new Workflow();
        workflow.setMode(WorkflowMode.FULL);
        workflow.setWorkflowName(null);
        workflow.setOrganization("ICGC-TCGA-PanCancer");
        workflow.setRepository("wdl-pcawg-sanger-cgp-workflow");
        workflow.setSourceControl(SourceControl.GITHUB);
        workflow.setPath("github.com/ICGC-TCGA-PanCancer/wdl-pcawg-sanger-cgp-workflow");
        workflow.setDescriptorType("wdl");
        workflow.setDefaultWorkflowPath("/pcawg-cgp-somatic-workflow.wdl");
        workflow.setDefaultTestParameterFilePath(null);
        workflow.addWorkflowVersion(actualWorkflowVersion1);
        workflow.addWorkflowVersion(actualWorkflowVersion2);
        workflow.setId(950);
        workflow.setAuthor(null);
        workflow.setDescription(null);
        workflow.setLabels(Collections.emptySortedSet());
        workflow.setUsers(Collections.emptySet());
        workflow.setEmail(null);
        workflow.setDefaultVersion("master");
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
        ToolVersion expectedToolVersion = new ToolVersion();
        expectedToolVersion.setName("master");
        expectedToolVersion
                .setUrl("http://localhost:8080/api/ga4gh/v2/tools/%23workflow%2Fgithub.com%2FICGC-TCGA-PanCancer%2Fwdl-pcawg-sanger-cgp-workflow/versions/master");
        expectedToolVersion.setId("#workflow/github.com/ICGC-TCGA-PanCancer/wdl-pcawg-sanger-cgp-workflow:master");
        List<ToolVersion.DescriptorTypeEnum> descriptorTypeList = new ArrayList<>();
        descriptorTypeList.add(ToolVersion.DescriptorTypeEnum.WDL);
        expectedToolVersion.setImage("");
        expectedToolVersion.setDescriptorType(descriptorTypeList);
        expectedToolVersion.setDockerfile(false);
        expectedToolVersion.setMetaVersion("Wed Dec 31 19:00:00 EST 1969");
        expectedToolVersion.setVerified(false);
        expectedToolVersion.setVerifiedSource("");
        List<ToolVersion> expectedToolVersions = new ArrayList<>();
        expectedToolVersions.add(expectedToolVersion);
        Tool expectedTool = new Tool();
        expectedTool
                .setUrl("http://localhost:8080/api/ga4gh/v2/tools/%23workflow%2Fgithub.com%2FICGC-TCGA-PanCancer%2Fwdl-pcawg-sanger-cgp-workflow");
        expectedTool.setId("#workflow/github.com/ICGC-TCGA-PanCancer/wdl-pcawg-sanger-cgp-workflow");
        expectedTool.setOrganization("ICGC-TCGA-PanCancer");
        expectedTool.setToolname("wdl-pcawg-sanger-cgp-workflow");
        expectedTool.setToolclass(ToolClassesApiServiceImpl.getWorkflowClass());
        expectedTool.setDescription("");
        expectedTool.setAuthor("Unknown author");
        expectedTool.setMetaVersion("Wed Dec 31 19:00:00 EST 1969");
        expectedTool.setContains(Collections.EMPTY_LIST);
        expectedTool.setVerified(false);
        expectedTool.setVerifiedSource("[]");
        expectedTool.setSigned(false);
        expectedTool.setVersions(expectedToolVersions);
        assertEquals(expectedTool, actualTool);
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
