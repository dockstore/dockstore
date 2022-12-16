/*
 * Copyright 2022 OICR and UCSC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.dockstore.webservice.helpers.statelisteners;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.SourceControl;
import io.dockstore.webservice.core.AppTool;
import io.dockstore.webservice.core.BioWorkflow;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.core.Workflow;
import io.dockstore.webservice.core.WorkflowVersion;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;

public class ElasticListenerTest {

    private static final String FIRST_VERSION_NAME = "First";
    private static final String SECOND_VERSION_NAME = "Second";
    private static final int FIRST_VERSION_ID = 1;
    private static final int SECOND_VERSION_ID = 2;
    private WorkflowVersion firstWorkflowVersion;
    private WorkflowVersion secondWorkflowVersion;
    private Tag firstTag;
    private Tag secondTag;
    private WorkflowVersion firstAppToolVersion;
    private WorkflowVersion secondAppToolVersion;
    private BioWorkflow bioWorkflow;
    private Tool tool;
    private AppTool appTool;


    @Before
    public void setup() throws IllegalAccessException {

        bioWorkflow = new BioWorkflow();
        initEntry(bioWorkflow);
        firstWorkflowVersion = new WorkflowVersion();
        secondWorkflowVersion = new WorkflowVersion();
        initVersion(firstWorkflowVersion, FIRST_VERSION_NAME, FIRST_VERSION_ID, List.of("1.0"));
        initVersion(secondWorkflowVersion, SECOND_VERSION_NAME,
            SECOND_VERSION_ID, List.of("1.1"));
        bioWorkflow.getWorkflowVersions().addAll(List.of(firstWorkflowVersion,
            secondWorkflowVersion));

        tool = new Tool();
        initEntry(tool);
        firstTag = new Tag();
        secondTag = new Tag();
        initVersion(firstTag, FIRST_VERSION_NAME, FIRST_VERSION_ID, List.of("1.0"));
        initVersion(secondTag, SECOND_VERSION_NAME, SECOND_VERSION_ID, List.of("1.0"));
        tool.getWorkflowVersions().addAll(List.of(firstTag, secondTag));

        appTool = new AppTool();
        initEntry(appTool);
        firstAppToolVersion = new WorkflowVersion();
        secondAppToolVersion = new WorkflowVersion();
        initVersion(firstAppToolVersion, FIRST_VERSION_NAME, FIRST_VERSION_ID, List.of());
        initVersion(secondAppToolVersion, SECOND_VERSION_NAME, SECOND_VERSION_ID, List.of());
        appTool.getWorkflowVersions().addAll(List.of(firstAppToolVersion, secondAppToolVersion));
    }

    private void initEntry(Entry entry) {
        if (entry instanceof Tool) {
            Tool toolEntry = (Tool)entry;
            toolEntry.setDescriptorType(List.of(DescriptorLanguage.WDL.toString()));
        } else if (entry instanceof Workflow) {
            Workflow workflowOrAppTool = (Workflow)entry;
            workflowOrAppTool.setDescriptorType(DescriptorLanguage.WDL);
            workflowOrAppTool.setSourceControl(SourceControl.GITHUB);
            workflowOrAppTool.setOrganization("potato");
            workflowOrAppTool.setRepository("foobar");
        }
    }

    private void initVersion(final Version version, final String name, final long id, List<String> descriptorTypeVersions)
        throws IllegalAccessException {
        version.setName(name);
        final SourceFile sourceFile = new SourceFile();
        sourceFile.setPath("/Dockstore.wdl");
        sourceFile.setContent("Doesn't matter");
        version.getSourceFiles().add(sourceFile);
        version.setDescriptorTypeVersions(descriptorTypeVersions);
        // Id is normally set via Hibernate generator; have to use reflection to set it, alas
        FieldUtils.writeField(version, "id", id, true);
    }

    @Test
    public void testNoValidVersions() {
        // If there are no valid versions, the latest version id wins out
        List.of(bioWorkflow, tool, appTool).stream().forEach(entry -> {
            final Entry detachedEntry = ElasticListener.removeIrrelevantProperties(entry);
            validateOnlyOneVersionHasSourceFileContent(detachedEntry, SECOND_VERSION_NAME);
        });
    }

    @Test
    public void testNoVersions() {
        // In theory I don't think this should happen with a published entry, but just in case...
        List.of(bioWorkflow, tool, appTool).stream().forEach(entry -> {
            entry.getWorkflowVersions().clear();
            // Just make sure it doesn't throw an exception
            ElasticListener.removeIrrelevantProperties(entry);
        });
    }

    @Test
    public void testDefaultVersionSet() {
        bioWorkflow.setActualDefaultVersion(firstWorkflowVersion);
        validateOnlyOneVersionHasSourceFileContent(ElasticListener.removeIrrelevantProperties(bioWorkflow),
            FIRST_VERSION_NAME);
        bioWorkflow.setActualDefaultVersion(secondWorkflowVersion);
        validateOnlyOneVersionHasSourceFileContent(ElasticListener.removeIrrelevantProperties(bioWorkflow),
            SECOND_VERSION_NAME);

        tool.setActualDefaultVersion(firstTag);
        validateOnlyOneVersionHasSourceFileContent(ElasticListener.removeIrrelevantProperties(tool),
            FIRST_VERSION_NAME);
        tool.setActualDefaultVersion(secondTag);
        validateOnlyOneVersionHasSourceFileContent(ElasticListener.removeIrrelevantProperties(tool),
            SECOND_VERSION_NAME);

        appTool.setActualDefaultVersion(firstAppToolVersion);
        validateOnlyOneVersionHasSourceFileContent(ElasticListener.removeIrrelevantProperties(appTool),
            FIRST_VERSION_NAME);
        appTool.setActualDefaultVersion(secondAppToolVersion);
        validateOnlyOneVersionHasSourceFileContent(ElasticListener.removeIrrelevantProperties(appTool),
            SECOND_VERSION_NAME);
    }

    @Test
    public void testValidVersionsNoDefault() {
        firstWorkflowVersion.setValid(true);
        firstTag.setValid(true);
        firstAppToolVersion.setValid(true);
        List.of(bioWorkflow, tool, appTool).stream().forEach(entry -> {
            validateOnlyOneVersionHasSourceFileContent(ElasticListener.removeIrrelevantProperties(entry),
                FIRST_VERSION_NAME);

        });
    }

    private void validateOnlyOneVersionHasSourceFileContent(final Entry entry, final String versionName) {
        entry.getWorkflowVersions().forEach(v -> {
            final Version version = (Version) v;
            if (version.getName().equals(versionName)) {
                version.getSourceFiles().forEach(sf -> assertTrue(!((SourceFile)sf).getContent().isEmpty()));
            } else {
                version.getSourceFiles().forEach(sf -> assertTrue(((SourceFile)sf).getContent().isEmpty()));
            }
        });
    }

    @Test
    public void testDescriptorTypeVersionsSet() throws IOException {
        // bioWorkflow has two descriptor type versions
        JsonNode entry = ElasticListener.dockstoreEntryToElasticSearchObject(bioWorkflow);
        JsonNode descriptorTypeVersions = entry.get("descriptor_type_versions");
        assertEquals(2, descriptorTypeVersions.size());
        List<String> esObjectDescriptorTypeVersions = getDescriptorTypeVersionsFromJsonNode(descriptorTypeVersions);
        assertTrue(esObjectDescriptorTypeVersions.contains("WDL 1.0") && esObjectDescriptorTypeVersions.contains("WDL 1.1"));

        // tool has one descriptor type version
        entry = ElasticListener.dockstoreEntryToElasticSearchObject(tool);
        descriptorTypeVersions = entry.get("descriptor_type_versions");
        assertEquals(1, descriptorTypeVersions.size());
        esObjectDescriptorTypeVersions = getDescriptorTypeVersionsFromJsonNode(descriptorTypeVersions);
        assertTrue(esObjectDescriptorTypeVersions.contains("WDL 1.0"));

        // tool has CWL and WDL
        tool.setDescriptorType(List.of(DescriptorLanguage.WDL.toString(), DescriptorLanguage.CWL.toString()));
        assertEquals(2, tool.getDescriptorType().size());
        entry = ElasticListener.dockstoreEntryToElasticSearchObject(tool);
        descriptorTypeVersions = entry.get("descriptor_type_versions");
        assertEquals("Should not have descriptor type versions if there's more than one language", 0, descriptorTypeVersions.size());

        // appTool has one descriptor type version
        entry = ElasticListener.dockstoreEntryToElasticSearchObject(appTool);
        descriptorTypeVersions = entry.get("descriptor_type_versions");
        assertEquals(0, descriptorTypeVersions.size());
    }

    private List<String> getDescriptorTypeVersionsFromJsonNode(JsonNode descriptorTypeVersionsJsonNode) {
        List<String> descriptorTypeVersions = new ArrayList<>();
        for (JsonNode version : descriptorTypeVersionsJsonNode) {
            descriptorTypeVersions.add(version.textValue());
        }
        return descriptorTypeVersions;
    }
}
