/*
 *    Copyright 2020 OICR
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
package io.dockstore.common.yaml;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import io.dropwizard.testing.FixtureHelpers;
import org.apache.commons.io.IOUtils;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DockstoreYamlTest {
    private static final String DOCKSTORE10_YAML = FixtureHelpers.fixture("fixtures/dockstore10.yml");
    private static final String DOCKSTORE11_YAML = FixtureHelpers.fixture("fixtures/dockstore11.yml");
    private static final String DOCKSTORE12_YAML = FixtureHelpers.fixture("fixtures/dockstore12.yml");

    @Test
    public void testFindVersion() {
        assertTrue(DockstoreYamlHelper.findValidVersion("abc").isEmpty());
        assertTrue(DockstoreYamlHelper.findValidVersion("service: 1.0 garbage").isEmpty());
        assertTrue(DockstoreYamlHelper.findValidVersion("#service: 1.0").isEmpty());
        assertEquals(DockstoreYamlHelper.Version.ONE_ZERO, DockstoreYamlHelper.findValidVersion("version: 1.0").get());
        assertEquals(DockstoreYamlHelper.Version.ONE_ZERO, DockstoreYamlHelper.findValidVersion("#Comment\nversion: 1.0").get());
        assertEquals(DockstoreYamlHelper.Version.ONE_ONE, DockstoreYamlHelper.findValidVersion("#Comment\nversion: 1.1").get());
        assertEquals(DockstoreYamlHelper.Version.ONE_TWO, DockstoreYamlHelper.findValidVersion("#Comment\nversion: 1.2").get());
        assertTrue("1.3", DockstoreYamlHelper.findValidVersion("#Comment\nversion: 1.3").isEmpty());
        assertEquals(DockstoreYamlHelper.Version.ONE_ZERO, DockstoreYamlHelper.findValidVersion(DOCKSTORE10_YAML).get());
    }

    @Test
    public void testReadDockstore10Yaml() throws DockstoreYamlHelper.DockstoreYamlException {
        final DockstoreYaml10 dockstoreYaml = (DockstoreYaml10)DockstoreYamlHelper.readDockstoreYaml(DOCKSTORE10_YAML);
        assertEquals("SmartSeq2SingleSample.wdl", dockstoreYaml.primaryDescriptor);
    }

    @Test
    public void testReadDockstore11Yaml() throws DockstoreYamlHelper.DockstoreYamlException {
        final DockstoreYaml dockstoreYaml = DockstoreYamlHelper.readDockstoreYaml(DOCKSTORE11_YAML);
        assertTrue(dockstoreYaml.getClass() == DockstoreYaml11.class);
        DockstoreYaml11 dockstoreYaml11 = (DockstoreYaml11)dockstoreYaml;
        assertEquals("7222", dockstoreYaml11.getService().getEnvironment().get("httpPort").getDefault());
        final YamlService11.DataSet dataset1 = dockstoreYaml11.getService().getData().get("dataset_1");
        assertEquals("xena/files", dataset1.getTargetDirectory());
        final YamlService11.FileDesc tsv = dataset1.getFiles().get("tsv");
        assertEquals("Data for Xena in TSV format", tsv.getDescription());
    }

    @Test
    public void testReadDockstoreYaml12() throws DockstoreYamlHelper.DockstoreYamlException {
        final DockstoreYaml12 dockstoreYaml = (DockstoreYaml12)DockstoreYamlHelper.readDockstoreYaml(DOCKSTORE12_YAML);
        final List<YamlWorkflow> workflows = dockstoreYaml.getWorkflows();
        assertEquals(2, workflows.size());
        final Optional<YamlWorkflow> workflowFoobar = workflows.stream().filter(w -> w.getName().equals("foobar")).findFirst();
        if (!workflowFoobar.isPresent()) {
            fail("Could not find workflow foobar");
        }
        final YamlWorkflow workflow = workflowFoobar.get();
        assertEquals("wdl", workflow.getSubclass());
        assertEquals("/Dockstore2.wdl", workflow.getPrimaryDescriptorPath());
        final List<String> testParameterFiles = workflow.getTestParameterFiles();
        assertEquals(1, testParameterFiles.size());
        assertEquals("/dockstore.wdl.json", testParameterFiles.get(0));
        final List<Service12> services = dockstoreYaml.getServices();
        assertEquals(1, services.size());
    }

    @Test
    public void testMissingPrimaryDescriptor() {
        try {
            final String content = DOCKSTORE10_YAML.replaceFirst("(?m)^primaryDescriptor.*$", "");
            DockstoreYamlHelper.readDockstoreYaml(content);
            fail("Invalid dockstore.yml not caught");
        } catch (DockstoreYamlHelper.DockstoreYamlException e) {
            System.out.println(e.getMessage());
        }
    }

    @Test
    public void testInvalidSubclass() {
        final String content = DOCKSTORE12_YAML.replace("docker-compose", "invalid sub class");
        try {
            final DockstoreYaml12 dockstoreYaml = (DockstoreYaml12)DockstoreYamlHelper.readDockstoreYaml(content);
            fail("Did not catch invalid subclass");
        } catch (DockstoreYamlHelper.DockstoreYamlException e) {
            assertEquals(Service12.SUBCLASS_ERROR, e.getMessage());
        }
    }

    @Test
    public void testRead11As12() throws DockstoreYamlHelper.DockstoreYamlException {
        final DockstoreYaml12 dockstoreYaml12 = DockstoreYamlHelper.readAsDockstoreYaml12(DOCKSTORE11_YAML);
        assertEquals(0, dockstoreYaml12.getWorkflows().size());
        assertEquals(1, dockstoreYaml12.getServices().size());
    }

    @Test
    public void testEmptyDockstore12() {
        try {
            DockstoreYamlHelper.readAsDockstoreYaml12("version: 1.2");
            fail("Dockstore yaml with no services and no workflows should fail");
        } catch (DockstoreYamlHelper.DockstoreYamlException e) {
            assertEquals(ValidDockstore12.AT_LEAST_1_WORKFLOW_OR_SERVICE, e.getMessage());
        }
    }

    @Test
    public void testMalformedDockstoreYaml() throws IOException {
        final String content = IOUtils.toString(
                new URL("https://raw.githubusercontent.com/denis-yuen/test-malformed-app/c43103f4004241cb738280e54047203a7568a337/.dockstore.yml"),
                StandardCharsets.UTF_8);
        try {
            DockstoreYamlHelper.readAsDockstoreYaml12(content);
            fail("expected malformed dockstore.yml to fail");
        } catch (DockstoreYamlHelper.DockstoreYamlException e) {
            assertEquals(DockstoreYamlHelper.DOCKSTORE_YML_MISSING_VALID_VERSION, e.getMessage());
        }
    }

}
