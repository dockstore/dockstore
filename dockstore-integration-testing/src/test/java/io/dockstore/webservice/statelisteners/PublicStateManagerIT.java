/*
 * Copyright 2019 OICR
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.dockstore.webservice.statelisteners;

import static io.dockstore.common.DescriptorLanguage.FileType.DOCKSTORE_CWL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dockstore.common.CommonTestUtilities;
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
import io.dockstore.webservice.helpers.statelisteners.ElasticListener;
import io.dropwizard.jackson.Jackson;
import io.dropwizard.testing.ResourceHelpers;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;
import uk.org.webcompere.systemstubs.stream.output.NoopStream;

@ExtendWith(SystemStubsExtension.class)
public class PublicStateManagerIT {
    private static PublicStateManager manager;
    private static ElasticSearchHelper esHelper;

    @SystemStub
    private final SystemOut systemOut = new SystemOut(new NoopStream());

    @SystemStub
    private final SystemErr systemErr = new SystemErr(new NoopStream());

    @BeforeAll
    public static void setupManager() {
        DockstoreWebserviceConfiguration config = new DockstoreWebserviceConfiguration();
        config.getEsConfiguration().setHostname("localhost");
        config.getEsConfiguration().setPort(9200);
        PublicStateManagerIT.manager = PublicStateManager.getInstance();
        manager.reset();
        manager.setConfig(config);
        esHelper = new ElasticSearchHelper(config.getEsConfiguration());
    }

    @BeforeEach
    public void before() throws Exception {
        CommonTestUtilities.restartElasticsearch();
    }

    @Test
    public void dockstoreEntryToElasticSearchObject() throws IOException {
        Tool tool = getFakeTool(false);
        JsonNode jsonNode = ElasticListener.dockstoreEntryToElasticSearchObject(tool);
        boolean verified = jsonNode.get("verified").booleanValue();
        assertFalse(verified);
        tool = getFakeTool(true);
        final ObjectMapper mapper = Jackson.newObjectMapper();
        String beforeString = mapper.writeValueAsString(tool);
        jsonNode = ElasticListener.dockstoreEntryToElasticSearchObject(tool);
        String afterString = mapper.writeValueAsString(tool);
        assertEquals(beforeString, afterString, "The original tool should not have changed.");
        verified = jsonNode.get("verified").booleanValue();
        assertTrue(verified);
    }

    private Tool getFakeTool(boolean verified) throws IOException {
        Tool tool = new Tool();
        Tag tag = getFakeTag(verified);
        tool.setRegistry("potato");
        tool.addWorkflowVersion(tag);
        tool.setActualDefaultVersion(tag);
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
    public void addAnEntry() throws Exception {
        Tool tool = getFakeTool(false);
        manager.handleIndexUpdate(tool, StateManagerMode.UPDATE);

        esHelper.start(); // Need to start the elasticsearch client because ElasticListener.bulkUpsert relies on it
        manager.bulkUpsert(Collections.singletonList(tool));
        esHelper.stop();

        //TODO: should extend this by checking that elastic search holds the content we expect
        assertFalse(systemOut.getText().contains("Connection refused"));
    }

    @Test
    public void addAService() throws Exception {
        manager.handleIndexUpdate(new Service(), StateManagerMode.UPDATE);
        assertFalse(systemOut.getText().contains("Performing index update"));
    }

    @Test
    public void filterCheckerWorkflows() {
        Workflow checkerWorkflow = new BioWorkflow();
        checkerWorkflow.setIsChecker(true);
        Workflow workflow = new BioWorkflow();
        workflow.setIsChecker(false);
        Tool tool = new Tool();
        List<Entry> entries = ElasticListener.filterCheckerWorkflows(Arrays.asList(workflow, tool, checkerWorkflow));
        assertEquals(2, entries.size(), "There should've been 2 entries without the checker workflow");
        entries.forEach(entry -> assertFalse(entry instanceof Workflow && ((Workflow)entry).isIsChecker(), "There should be no checker workflows"));
    }

}
