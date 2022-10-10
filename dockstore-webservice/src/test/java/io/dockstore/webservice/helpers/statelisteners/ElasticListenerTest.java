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

import static org.junit.Assert.assertTrue;

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
import org.junit.Before;
import org.junit.Test;

public class ElasticListenerTest {

    private static final String FIRST_VERSION_NAME = "First";
    private static final String SECOND_VERSION_NAME = "Second";
    private static final int FIRST_VERSION_ID = 1;
    private static final int SECOND_VERSION_ID = 2;
    private WorkflowVersion FIRST_WORKFLOW_VERSION;
    private WorkflowVersion SECOND_WORKFLOW_VERSION;
    private Tag FIRST_TAG;
    private Tag SECOND_TAG;
    private WorkflowVersion FIRST_APP_TOOL_VERSION;
    private WorkflowVersion SECOND_APP_TOOL_VERSION;
    private BioWorkflow bioWorkflow;
    private Tool tool;
    private AppTool appTool;


    @Before
    public void setup() throws IllegalAccessException {

        bioWorkflow = new BioWorkflow();
        FIRST_WORKFLOW_VERSION = new WorkflowVersion();
        SECOND_WORKFLOW_VERSION = new WorkflowVersion();
        initVersion(FIRST_WORKFLOW_VERSION, FIRST_VERSION_NAME, FIRST_VERSION_ID);
        initVersion(SECOND_WORKFLOW_VERSION, SECOND_VERSION_NAME,
            SECOND_VERSION_ID);
        bioWorkflow.getWorkflowVersions().addAll(List.of(FIRST_WORKFLOW_VERSION,
            SECOND_WORKFLOW_VERSION));

        tool = new Tool();
        FIRST_TAG = new Tag();
        SECOND_TAG = new Tag();
        initVersion(FIRST_TAG, FIRST_VERSION_NAME, FIRST_VERSION_ID);
        initVersion(SECOND_TAG, SECOND_VERSION_NAME, SECOND_VERSION_ID);
        tool.getWorkflowVersions().addAll(List.of(FIRST_TAG, SECOND_TAG));

        appTool = new AppTool();
        FIRST_APP_TOOL_VERSION = new WorkflowVersion();
        SECOND_APP_TOOL_VERSION = new WorkflowVersion();
        initVersion(FIRST_APP_TOOL_VERSION, FIRST_VERSION_NAME, FIRST_VERSION_ID);
        initVersion(SECOND_APP_TOOL_VERSION, SECOND_VERSION_NAME, SECOND_VERSION_ID);
        appTool.getWorkflowVersions().addAll(List.of(FIRST_APP_TOOL_VERSION, SECOND_APP_TOOL_VERSION));
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
        bioWorkflow.setActualDefaultVersion(FIRST_WORKFLOW_VERSION);
        validateOnlyOneVersionHasSourceFileContent(ElasticListener.removeIrrelevantProperties(bioWorkflow),
            FIRST_VERSION_NAME);
        bioWorkflow.setActualDefaultVersion(SECOND_WORKFLOW_VERSION);
        validateOnlyOneVersionHasSourceFileContent(ElasticListener.removeIrrelevantProperties(bioWorkflow),
            SECOND_VERSION_NAME);

        tool.setActualDefaultVersion(FIRST_TAG);
        validateOnlyOneVersionHasSourceFileContent(ElasticListener.removeIrrelevantProperties(tool),
            FIRST_VERSION_NAME);
        tool.setActualDefaultVersion(SECOND_TAG);
        validateOnlyOneVersionHasSourceFileContent(ElasticListener.removeIrrelevantProperties(tool),
            SECOND_VERSION_NAME);

        appTool.setActualDefaultVersion(FIRST_APP_TOOL_VERSION);
        validateOnlyOneVersionHasSourceFileContent(ElasticListener.removeIrrelevantProperties(appTool),
            FIRST_VERSION_NAME);
        appTool.setActualDefaultVersion(SECOND_APP_TOOL_VERSION);
        validateOnlyOneVersionHasSourceFileContent(ElasticListener.removeIrrelevantProperties(appTool),
            SECOND_VERSION_NAME);
    }

    @Test
    public void testValidVersionsNoDefault() {
        FIRST_WORKFLOW_VERSION.setValid(true);
        FIRST_TAG.setValid(true);
        FIRST_APP_TOOL_VERSION.setValid(true);
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

}
