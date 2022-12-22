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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.dockstore.webservice.core.AppTool;
import io.dockstore.webservice.core.BioWorkflow;
import io.dockstore.webservice.core.Entry;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.core.WorkflowVersion;
import java.util.List;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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


    @BeforeEach
    public void setup() throws IllegalAccessException {

        bioWorkflow = new BioWorkflow();
        firstWorkflowVersion = new WorkflowVersion();
        secondWorkflowVersion = new WorkflowVersion();
        initVersion(firstWorkflowVersion, FIRST_VERSION_NAME, FIRST_VERSION_ID);
        initVersion(secondWorkflowVersion, SECOND_VERSION_NAME,
            SECOND_VERSION_ID);
        bioWorkflow.getWorkflowVersions().addAll(List.of(firstWorkflowVersion,
            secondWorkflowVersion));

        tool = new Tool();
        firstTag = new Tag();
        secondTag = new Tag();
        initVersion(firstTag, FIRST_VERSION_NAME, FIRST_VERSION_ID);
        initVersion(secondTag, SECOND_VERSION_NAME, SECOND_VERSION_ID);
        tool.getWorkflowVersions().addAll(List.of(firstTag, secondTag));

        appTool = new AppTool();
        firstAppToolVersion = new WorkflowVersion();
        secondAppToolVersion = new WorkflowVersion();
        initVersion(firstAppToolVersion, FIRST_VERSION_NAME, FIRST_VERSION_ID);
        initVersion(secondAppToolVersion, SECOND_VERSION_NAME, SECOND_VERSION_ID);
        appTool.getWorkflowVersions().addAll(List.of(firstAppToolVersion, secondAppToolVersion));
    }

    private void initVersion(final Version version, final String name, final long id)
        throws IllegalAccessException {
        version.setName(name);
        final SourceFile sourceFile = new SourceFile();
        sourceFile.setPath("/Dockstore.wdl");
        sourceFile.setContent("Doesn't matter");
        version.getSourceFiles().add(sourceFile);
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
                version.getSourceFiles().forEach(sf -> assertFalse(((SourceFile) sf).getContent().isEmpty()));
            } else {
                version.getSourceFiles().forEach(sf -> assertTrue(((SourceFile)sf).getContent().isEmpty()));
            }
        });
    }

}
