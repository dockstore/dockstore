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
    private BioWorkflow bioWorkflow;
    private Tool tool;
    private AppTool appTool;


    @Before
    public void setup() throws IllegalAccessException {
        bioWorkflow = new BioWorkflow();
        tool = new Tool();
        appTool = new AppTool();
        FIRST_WORKFLOW_VERSION = new WorkflowVersion();
        SECOND_WORKFLOW_VERSION = new WorkflowVersion();
        initVersion(FIRST_WORKFLOW_VERSION, FIRST_VERSION_NAME, FIRST_VERSION_ID);
        initVersion(SECOND_WORKFLOW_VERSION, SECOND_VERSION_NAME,
            SECOND_VERSION_ID);
    }

    private void initVersion(final WorkflowVersion version, final String name, final long id)
        throws IllegalAccessException {
        version.setName(name);
        final SourceFile sourceFile = new SourceFile();
        sourceFile.setPath("/Dockstore.wdl");
        sourceFile.setContent("Doesn't matter");
        version.getSourceFiles().add(sourceFile);
        // Id is normally set via Hibernate generator; have to use reflection to set it, alas
        FieldUtils.writeField(version, "id", id, true);
        bioWorkflow.getWorkflowVersions().addAll(List.of(FIRST_WORKFLOW_VERSION,
            SECOND_WORKFLOW_VERSION));
    }

    @Test
    public void testNoValidVersions() {
        // If there are no valid versions, the latest version id wins out
        final Entry entry = ElasticListener.removeIrrelevantProperties(bioWorkflow);
        validateOnlyOneVersionHasSourceFileContent(entry, SECOND_WORKFLOW_VERSION);
    }

    @Test
    public void testDefaultVersionSet() {
        bioWorkflow.setActualDefaultVersion(FIRST_WORKFLOW_VERSION);
        validateOnlyOneVersionHasSourceFileContent(ElasticListener.removeIrrelevantProperties(bioWorkflow),
            FIRST_WORKFLOW_VERSION);
        bioWorkflow.setActualDefaultVersion(SECOND_WORKFLOW_VERSION);
        validateOnlyOneVersionHasSourceFileContent(ElasticListener.removeIrrelevantProperties(bioWorkflow),
            SECOND_WORKFLOW_VERSION);
    }

    @Test
    public void testValidVersionsNoDefault() {
        // If only one version is valid, it wins out
        FIRST_WORKFLOW_VERSION.setValid(true);
        validateOnlyOneVersionHasSourceFileContent(ElasticListener.removeIrrelevantProperties(bioWorkflow),
            FIRST_WORKFLOW_VERSION);

        // If more than one version is valid, the highest id wins out
        SECOND_WORKFLOW_VERSION.setValid(true);
        validateOnlyOneVersionHasSourceFileContent(ElasticListener.removeIrrelevantProperties(bioWorkflow),
            SECOND_WORKFLOW_VERSION);
    }

    private void validateOnlyOneVersionHasSourceFileContent(final Entry entry, final WorkflowVersion version) {
        entry.getWorkflowVersions().forEach(v -> {
            final Version v1 = (Version) v;
            if (v1.getName().equals(version.getName())) {
                v1.getSourceFiles().forEach(sf -> assertTrue(!((SourceFile)sf).getContent().isEmpty()));
            } else {
                v1.getSourceFiles().forEach(sf -> assertTrue(((SourceFile)sf).getContent().isEmpty()));
            }
        });
    }

}
