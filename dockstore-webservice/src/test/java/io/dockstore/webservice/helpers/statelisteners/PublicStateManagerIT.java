/*
 *    Copyright 2019 OICR
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
package io.dockstore.webservice.helpers.statelisteners;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.BioWorkflow;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.Service;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.helpers.ElasticSearchHelper;
import io.dockstore.webservice.helpers.PublicStateManager;
import io.dockstore.webservice.helpers.StateManagerMode;
import io.dropwizard.jackson.Jackson;
import io.dropwizard.testing.ResourceHelpers;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;

import static io.dockstore.common.DescriptorLanguage.FileType.DOCKSTORE_CWL;

public class PublicStateManagerIT {
    private static PublicStateManager manager;

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();

    @BeforeClass
    public static void setupManager() {
        DockstoreWebserviceConfiguration config = new DockstoreWebserviceConfiguration();
        config.getEsConfiguration().setHostname("localhost");
        config.getEsConfiguration().setPort(9200);
        PublicStateManagerIT.manager = PublicStateManager.getInstance();
        manager.setConfig(config);
        ElasticSearchHelper.setConfig(config.getEsConfiguration());
    }

    @Test
    public void dockstoreEntryToElasticSearchObject() throws IOException {
        Tool tool = getFakeTool(false);
        JsonNode jsonNode = ElasticListener.dockstoreEntryToElasticSearchObject(tool);
        boolean verified = jsonNode.get("verified").booleanValue();
        Assert.assertFalse(verified);
        tool = getFakeTool(true);
        final ObjectMapper mapper = Jackson.newObjectMapper();
        String beforeString = mapper.writeValueAsString(tool);
        jsonNode = ElasticListener.dockstoreEntryToElasticSearchObject(tool);
        String afterString = mapper.writeValueAsString(tool);
        Assert.assertEquals("The original tool should not have changed.", beforeString, afterString);
        verified = jsonNode.get("verified").booleanValue();
        Assert.assertTrue(verified);
    }

    private Tool getFakeTool(boolean verified) throws IOException {
        Tool tool = new Tool();
        Tag fakeTag1 = getFakeTag(verified);
        Tag fakeTag2 = getFakeTag(verified);
        fakeTag2.setName("notthedefault");
        fakeTag2.setReference("notthedefault");
        tool.setRegistry("potato");
        tool.addWorkflowVersion(fakeTag1);
        tool.addWorkflowVersion(fakeTag2);
        tool.setActualDefaultVersion(fakeTag1);
        tool.setIsPublished(true);
        return tool;
    }

    private Tag getFakeTag(boolean verified) throws IOException {
        Tag tag = new Tag();
        SourceFile file = new SourceFile();
        File cwlFilePath = new File(ResourceHelpers.resourceFilePath("schema.cwl"));
        String cwlContent = Files.readString(cwlFilePath.toPath());
        file.setPath("dummypath");
        file.setAbsolutePath("/dummypath");
        file.setContent(cwlContent);
        file.setType(DOCKSTORE_CWL);
        if (verified) {
            Map<String, SourceFile.VerificationInformation> verifiedBySource = new HashMap<>();
            SourceFile.VerificationInformation verificationInformation = new SourceFile.VerificationInformation();
            verificationInformation.verified = true;
            verificationInformation.platformVersion = "1.7.0";
            verificationInformation.metadata = "Dockstore team";
            verifiedBySource.put("Dockstore CLI", verificationInformation);
            file.setVerifiedBySource(verifiedBySource);
        }
        tag.addSourceFile(file);
        tag.setReference("master");
        tag.updateVerified();
        return tag;
    }


    @Test
    public void addAnEntry() throws IOException {
        Tool tool = getFakeTool(false);
        manager.handleIndexUpdate(tool, StateManagerMode.UPDATE);

        manager.bulkUpsert(Collections.singletonList(tool));

        //TODO: should extend this by checking that elastic search holds the content we expect
        Assert.assertFalse(systemOutRule.getLog().contains("Connection refused"));
    }

    @Test
    public void addAService() {
        manager.handleIndexUpdate(new Service(), StateManagerMode.UPDATE);
        Assert.assertFalse(systemOutRule.getLog().contains("Performing index update"));
    }

    @Test
    public void filterCheckerWorkflows() {
        Workflow checkerWorkflow = new BioWorkflow();
        checkerWorkflow.setIsChecker(true);
        Workflow workflow = new BioWorkflow();
        workflow.setIsChecker(false);
        Tool tool = new Tool();
        List<Entry> entries = ElasticListener.filterCheckerWorkflows(Arrays.asList(workflow, tool, checkerWorkflow));
        Assert.assertEquals("There should've been 2 entries without the checker workflow", 2, entries.size());
        entries.forEach(entry -> Assert.assertFalse("There should be no checker workflows", entry instanceof Workflow && ((Workflow)entry).isIsChecker()));
    }

}
