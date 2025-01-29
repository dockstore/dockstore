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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import io.dockstore.common.FixtureUtility;
import io.dockstore.common.MuteForSuccessfulTests;
import io.dockstore.common.yaml.DockstoreYamlHelper.Version;
import io.dockstore.common.yaml.constraints.HasEntry12;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;

@ExtendWith(SystemStubsExtension.class)
@ExtendWith(MuteForSuccessfulTests.class)
class DockstoreYamlTest {
    private static final String DOCKSTORE10_YAML = FixtureUtility.fixture("fixtures/dockstore10.yml");
    private static final String DOCKSTORE11_YAML = FixtureUtility.fixture("fixtures/dockstore11.yml");
    private static final String DOCKSTORE12_YAML = FixtureUtility.fixture("fixtures/dockstore12.yml");
    private static final String DOCKSTORE_GALAXY_YAML = FixtureUtility.fixture("fixtures/dockstoreGalaxy.yml");

    @SystemStub
    public final SystemOut systemOut = new SystemOut();

    @SystemStub
    public final SystemErr systemErr = new SystemErr();

    @Test
    void testFindVersion() {
        assertTrue(DockstoreYamlHelper.findValidVersion("abc").isEmpty());
        assertTrue(DockstoreYamlHelper.findValidVersion("service: 1.0 garbage").isEmpty());
        assertTrue(DockstoreYamlHelper.findValidVersion("#service: 1.0").isEmpty());
        assertEquals(Version.ONE_ZERO, DockstoreYamlHelper.findValidVersion("version: 1.0").get());
        assertEquals(Version.ONE_ZERO, DockstoreYamlHelper.findValidVersion("#Comment\nversion: 1.0").get());
        assertEquals(Version.ONE_ONE, DockstoreYamlHelper.findValidVersion("#Comment\nversion: 1.1").get());
        assertEquals(Version.ONE_TWO, DockstoreYamlHelper.findValidVersion("#Comment\nversion: 1.2").get());
        assertTrue(DockstoreYamlHelper.findValidVersion("#Comment\nversion: 1.3").isEmpty(), "1.3");
        assertEquals(Version.ONE_ZERO, DockstoreYamlHelper.findValidVersion(DOCKSTORE10_YAML).get());
    }

    @Test
    void testReadDockstore10Yaml() throws DockstoreYamlHelper.DockstoreYamlException {
        final DockstoreYaml10 dockstoreYaml = (DockstoreYaml10)DockstoreYamlHelper.readDockstoreYaml(DOCKSTORE10_YAML, true);
        assertEquals("SmartSeq2SingleSample.wdl", dockstoreYaml.primaryDescriptor);
    }

    @Test
    void testReadDockstore11Yaml() throws DockstoreYamlHelper.DockstoreYamlException {
        final DockstoreYaml dockstoreYaml = DockstoreYamlHelper.readDockstoreYaml(DOCKSTORE11_YAML, true);
        assertSame(dockstoreYaml.getClass(), DockstoreYaml11.class);
        DockstoreYaml11 dockstoreYaml11 = (DockstoreYaml11)dockstoreYaml;
        assertEquals("7222", dockstoreYaml11.getService().getEnvironment().get("httpPort").getDefault());
        final YamlService11.DataSet dataset1 = dockstoreYaml11.getService().getData().get("dataset_1");
        assertEquals("xena/files", dataset1.getTargetDirectory());
        final YamlService11.FileDesc tsv = dataset1.getFiles().get("tsv");
        assertEquals("Data for Xena in TSV format", tsv.getDescription());
    }

    @Test
    void testReadDockstoreYaml12() throws DockstoreYamlHelper.DockstoreYamlException {
        final DockstoreYaml12 dockstoreYaml = (DockstoreYaml12)DockstoreYamlHelper.readDockstoreYaml(DOCKSTORE12_YAML, true);
        final List<YamlWorkflow> workflows = dockstoreYaml.getWorkflows();
        assertEquals(3, workflows.size());
        final Optional<YamlWorkflow> workflowFoobar = workflows.stream().filter(w -> "foobar".equals(w.getName())).findFirst();
        if (workflowFoobar.isEmpty()) {
            fail("Could not find workflow foobar");
        }

        YamlWorkflow workflow = workflowFoobar.get();
        assertTrue(workflow.getPublish());
        assertNull(workflow.getReadMePath());
        assertEquals("wdl", workflow.getSubclass());
        assertEquals("/Dockstore2.wdl", workflow.getPrimaryDescriptorPath());
        final List<String> testParameterFiles = workflow.getTestParameterFiles();
        assertEquals(1, testParameterFiles.size());
        assertEquals("/dockstore.wdl.json", testParameterFiles.get(0));
        final Filters filters = workflow.getFilters();
        final List<String> branches = filters.getBranches();
        assertEquals(1, branches.size());
        assertEquals("develop", branches.get(0));
        final List<String> tags = filters.getTags();
        assertEquals(1, tags.size());
        assertEquals("gwas*", tags.get(0));
        List<YamlAuthor> authors = workflow.getAuthors();
        assertEquals(2, authors.size());
        assertEquals("0000-0002-6130-1021", authors.get(0).getOrcid());
        assertEquals("UCSC", authors.get(1).getAffiliation());

        workflow = workflows.get(1);
        assertFalse(workflow.getPublish());
        assertNotNull(workflow.getReadMePath());
        workflow = workflows.get(2);
        assertNull(workflow.getPublish());
        assertNotNull(workflow.getReadMePath());

        final Service12 service = dockstoreYaml.getService();
        assertNotNull(service);
        assertTrue(service.getPublish());
        authors = service.getAuthors();
        assertEquals(1, authors.size());
        assertEquals("Institute", authors.get(0).getRole());

        final List<YamlNotebook> notebooks = dockstoreYaml.getNotebooks();
        assertEquals(2, notebooks.size());

        final YamlNotebook notebook = notebooks.get(0);
        assertEquals("notebook0", notebook.getName());
        assertEquals("Jupyter", notebook.getFormat());
        assertEquals("Python", notebook.getLanguage());
        assertEquals("/notebook0.ipynb", notebook.getPath());
        assertEquals(null, notebook.getKernel());
        assertEquals(true, notebook.getPublish());
        assertTrue(notebook.getLatestTagAsDefault());
        assertEquals(List.of("branch0"), notebook.getFilters().getBranches());
        assertEquals(List.of("tag0"), notebook.getFilters().getTags());
        assertEquals(List.of("author0"), notebook.getAuthors().stream().map(YamlAuthor::getName).toList());
        assertEquals(List.of("/test0"), notebook.getTestParameterFiles());
        assertEquals(List.of("/other0"), notebook.getOtherFiles());

        final YamlNotebook notebook1 = notebooks.get(1);
        assertEquals("notebook1", notebook1.getName());
        assertEquals("jupyter", notebook1.getFormat());
        assertEquals("python", notebook1.getLanguage());
        assertEquals("/notebook1.ipynb", notebook1.getPath());
        assertEquals("quay.io/seqware/seqware_full/1.1", notebook1.getKernel());
        assertNull(notebook1.getPublish());
        assertFalse(notebook1.getLatestTagAsDefault());
        assertTrue(notebook1.getFilters().getBranches().isEmpty());
        assertTrue(notebook1.getFilters().getTags().isEmpty());
        assertTrue(notebook1.getAuthors().isEmpty());
        assertTrue(notebook1.getTestParameterFiles().isEmpty());
        assertTrue(notebook1.getOtherFiles().isEmpty());
    }

    @Test
    void testMalformedNotebookImage() {
        // parse yaml with zero-length notebook "image" field
        assertThrows(DockstoreYamlHelper.DockstoreYamlException.class,
            () -> DockstoreYamlHelper.readDockstoreYaml(DOCKSTORE12_YAML.replaceFirst("kernel: \\S++", "kernel: ''"), true));
        // parse yaml with notebook "image" field that contains whitespace
        assertThrows(DockstoreYamlHelper.DockstoreYamlException.class,
            () -> DockstoreYamlHelper.readDockstoreYaml(DOCKSTORE12_YAML.replaceFirst("kernel: \\S++", "kernel: 'an image'"), true));
    }

    @Test
    void testOptionalName() throws DockstoreYamlHelper.DockstoreYamlException {
        // create an input that contains a unnamed workflow and no service
        final String unnamedWorkflow = DOCKSTORE12_YAML.replace("name: bloop", "#").replaceFirst("(?s)service:.*$", "");
        final DockstoreYaml12 dockstoreYaml12 = DockstoreYamlHelper.readAsDockstoreYaml12(unnamedWorkflow);
        final List<YamlWorkflow> workflows = dockstoreYaml12.getWorkflows();
        assertEquals(3, workflows.size());
        assertEquals(2L, workflows.stream().filter(w -> w.getName() != null).count(), "Expecting two workflows with names");
        assertEquals(1L, workflows.stream().filter(w -> w.getName() == null).count(), "Expecting one workflow without a name");
    }

    @Test
    void testMissingPrimaryDescriptor() {
        try {
            final String content = DOCKSTORE10_YAML.replaceFirst("(?m)^primaryDescriptor.*$", "");
            DockstoreYamlHelper.readDockstoreYaml(content, true);
            fail("Invalid dockstore.yml not caught");
        } catch (DockstoreYamlHelper.DockstoreYamlException e) {
            // check that the error message contains the name of the property and an appropriate adjective
            assertTrue(e.getMessage().contains("primaryDescriptor"));
            assertTrue(e.getMessage().matches(".*([Mm]issing|[Rr]equired).*"));
        }
    }

    @Test
    void testRelativePrimaryDescriptor() {
        try {
            final String content = DOCKSTORE12_YAML.replaceAll("primaryDescriptorPath: /", "primaryDescriptorPath: ");
            DockstoreYamlHelper.readDockstoreYaml(content, true);
            fail("Invalid dockstore.yml not caught");
        } catch (DockstoreYamlHelper.DockstoreYamlException e) {
            // check that the error message contains the name of the property and an appropriate adjective
            assertTrue(e.getMessage().contains("primaryDescriptor"));
            assertTrue(e.getMessage().contains("must be an absolute path to be valid"));
        }
    }

    @Test
    void testRelativeTestDescriptorPaths() {
        try {
            final String content = DOCKSTORE12_YAML.replaceAll("- /", "- ");
            DockstoreYamlHelper.readDockstoreYaml(content, true);
            fail("Invalid dockstore.yml not caught");
        } catch (DockstoreYamlHelper.DockstoreYamlException e) {
            // check that the error message contains the name of the property and an appropriate adjective
            assertTrue(e.getMessage().contains("testParameterFiles"));
            assertTrue(e.getMessage().contains("must be an absolute path to be valid"));
        }
    }

    @Test
    void testInvalidSubclass() {
        final String content = DOCKSTORE12_YAML.replace("DOCKER_COMPOSE", "invalid sub class");
        try {
            DockstoreYamlHelper.readDockstoreYaml(content, true);
            fail("Did not catch invalid subclass");
        } catch (DockstoreYamlHelper.DockstoreYamlException e) {
            assertTrue(e.getMessage().contains("subclass"));
        }
    }

    @Test
    void testRead11As12() throws DockstoreYamlHelper.DockstoreYamlException {
        final DockstoreYaml12 dockstoreYaml12 = DockstoreYamlHelper.readAsDockstoreYaml12(DOCKSTORE11_YAML);
        assertEquals(0, dockstoreYaml12.getWorkflows().size());
        assertNotNull(dockstoreYaml12.getService());
    }

    @Test
    void testEmptyDockstore12() {
        try {
            DockstoreYamlHelper.readAsDockstoreYaml12("version: 1.2");
            fail("Dockstore yaml with no entries should fail");
        } catch (DockstoreYamlHelper.DockstoreYamlException e) {
            assertTrue(e.getMessage().contains(HasEntry12.AT_LEAST_1_WORKFLOW_OR_TOOL_OR_SERVICE));
        }
    }

    @Test
    void testEffectivelyEmptyDockstore12() {
        for (String emptyProperty: List.of("workflows", "tools", "service")) {
            try {
                DockstoreYamlHelper.readDockstoreYaml(String.format("version: 1.2\n%s:\n", emptyProperty), true);
                fail("Dockstore yaml with no entries should fail");
            } catch (DockstoreYamlHelper.DockstoreYamlException e) {
                assertTrue(e.getMessage().contains(HasEntry12.AT_LEAST_1_WORKFLOW_OR_TOOL_OR_SERVICE));
            }
        }
    }

    @Test
    void testMaliciousDockstore12() {
        // This test will show its not added, but doesn't prove it was never run. To do that
        // we need to set up a server that checks the classpath (or another payload with
        // simpler side effects?)
        try {
            DockstoreYamlHelper.readAsDockstoreYaml12("""
                version: 1.2
                workflows: !!javax.script.ScriptEngineManager [
                  !!java.net.URLClassLoader [[
                    !!java.net.URL ["https://localhost:3000"]
                  ]]
                ]
                """);
            fail("Dockstore yaml breaking entities should fail");
        } catch (DockstoreYamlHelper.DockstoreYamlException e) {
            // This message is emitted when SafeConstructor is used
            assertTrue(e.getMessage().contains("Global tag is not allowed"));
        }
    }

    @Test
    void testMalformedDockstoreYaml() throws IOException {
        final String spec = "https://raw.githubusercontent.com/denis-yuen/test-malformed-app/c43103f4004241cb738280e54047203a7568a337/"
                + ".dockstore.yml";
        final String content = IOUtils.toString(new URL(spec), StandardCharsets.UTF_8);
        try {
            DockstoreYamlHelper.readAsDockstoreYaml12(content);
            fail("expected malformed dockstore.yml to fail");
        } catch (DockstoreYamlHelper.DockstoreYamlException e) {
            assertEquals(DockstoreYamlHelper.DOCKSTORE_YML_MISSING_VALID_VERSION, e.getMessage());
        }
    }

    @Test
    void testMissingSubclass()  {
        // Replace:
        // ...
        // - subclass: DOCKER_COMPOSE
        //   name: UCSC...
        //
        // With:
        // ...
        // - name: UCSC...
        final String content = DOCKSTORE12_YAML.replaceFirst("(?m)^\\s*- subclass.*\\n", "").replaceFirst("    name: U", "  - name: U");
        try {
            DockstoreYamlHelper.readAsDockstoreYaml12(content);
        } catch (DockstoreYamlHelper.DockstoreYamlException e) {
            assertEquals(Service12.MISSING_SUBCLASS, e.getMessage());
        }
    }

    @Test
    void testWrongKeys() {
        try {
            DockstoreYamlHelper.readAsDockstoreYaml12(DOCKSTORE_GALAXY_YAML);
        } catch (DockstoreYamlHelper.DockstoreYamlException ex) {
            fail("Should be able to parse correctly");
        }

        final String workflowsKey = DOCKSTORE_GALAXY_YAML.replaceFirst("workflows", "workflow");
        try {
            DockstoreYamlHelper.readAsDockstoreYaml12(workflowsKey);
            fail("Shouldn't be able to parse correctly");
        } catch (DockstoreYamlHelper.DockstoreYamlException ex) {
            assertTrue(ex.getMessage().contains("must have at least 1 workflow, tool, or service"));
        }

        final String nameKey = DOCKSTORE_GALAXY_YAML.replaceFirst("name", "Name");
        try {
            DockstoreYamlHelper.readAsDockstoreYaml12(nameKey);
        } catch (DockstoreYamlHelper.DockstoreYamlException ex) {
            fail("'name' is not required and 'Name' should just be ignored.");
        }

        final String multipleKeys = DOCKSTORE_GALAXY_YAML.replaceFirst("version", "Version").replaceFirst("workflows", "workflow");
        try {
            DockstoreYamlHelper.readAsDockstoreYaml12(multipleKeys);
            fail("Shouldn't be able to parse correctly");
        } catch (DockstoreYamlHelper.DockstoreYamlException ex) {
            assertTrue(ex.getMessage().contains("missing valid version"), "Should only return first error");
        }
    }

    @Test
    void testDuplicateKeys() {
        try {
            DockstoreYamlHelper.readDockstoreYaml(DOCKSTORE12_YAML + "\nworkflows: []\n", true);
            fail("Should have thrown because of duplicate key");
        } catch (DockstoreYamlHelper.DockstoreYamlException ex) {
            assertTrue(ex.getMessage().contains("workflows"), "Error message should contain the name of the duplicate key");
        }
    }

    @Test
    void testDifferentCaseForWorkflowSubclass() throws DockstoreYamlHelper.DockstoreYamlException {
        final DockstoreYaml12 dockstoreYaml12 = DockstoreYamlHelper.readAsDockstoreYaml12(DOCKSTORE12_YAML);
        final List<YamlWorkflow> workflows = dockstoreYaml12.getWorkflows();
        assertEquals(3, workflows.size());
        assertEquals(1, workflows.stream().filter(w -> "CWL".equals(w.getSubclass())).count());
        assertEquals(1, workflows.stream().filter(w -> "cwl".equals(w.getSubclass())).count());
    }

    /**
     * Subclass in .dockstore.yml for Galaxy was `GXFORMAT2`; should be `GALAXY`.
     * Fixture has 2 of each, with different cases; make sure they all appear as `GXFORMAT2`, which our
     * other code relies on.
     * <p>
     * <a href="https://github.com/dockstore/dockstore/issues/3686">...</a>
     *
     */
    @Test
    void testGalaxySubclass() throws DockstoreYamlHelper.DockstoreYamlException {
        final List<YamlWorkflow> workflows = DockstoreYamlHelper.readAsDockstoreYaml12(DOCKSTORE_GALAXY_YAML).getWorkflows();
        assertEquals(4, workflows.stream().filter(w -> w.getSubclass().equalsIgnoreCase("gxformat2")).count());
    }

    @Test
    void testGitReferenceFilter() {
        // Empty filters allow anything
        Filters filters = new Filters();
        assertTrue(DockstoreYamlHelper.filterGitReference(Path.of("refs/heads/anything"), filters));
        assertTrue(DockstoreYamlHelper.filterGitReference(Path.of("refs/tags/any/thing"), filters));

        // Only match develop branch
        // glob
        filters.setBranches(List.of("develop"));
        assertTrue(DockstoreYamlHelper.filterGitReference(Path.of("refs/heads/develop"), filters));
        assertFalse(DockstoreYamlHelper.filterGitReference(Path.of("refs/heads/foo/develop"), filters));
        assertFalse(DockstoreYamlHelper.filterGitReference(Path.of("refs/heads/developfoo"), filters));
        assertFalse(DockstoreYamlHelper.filterGitReference(Path.of("refs/tags/develop"), filters));
        // regex
        filters.setBranches(List.of());
        filters.setBranches(List.of("/develop/"));
        assertTrue(DockstoreYamlHelper.filterGitReference(Path.of("refs/heads/develop"), filters));
        assertFalse(DockstoreYamlHelper.filterGitReference(Path.of("refs/heads/foo/develop"), filters));
        assertFalse(DockstoreYamlHelper.filterGitReference(Path.of("refs/heads/developfoo"), filters));
        assertFalse(DockstoreYamlHelper.filterGitReference(Path.of("refs/tags/develop"), filters));

        // Only match 0.1 tag
        // glob
        filters.setBranches(List.of());
        filters.setTags(List.of("0.1"));
        assertTrue(DockstoreYamlHelper.filterGitReference(Path.of("refs/tags/0.1"), filters));
        assertFalse(DockstoreYamlHelper.filterGitReference(Path.of("refs/tags/foo/0.1"), filters));
        assertFalse(DockstoreYamlHelper.filterGitReference(Path.of("refs/tags/0.1/foo"), filters));
        assertFalse(DockstoreYamlHelper.filterGitReference(Path.of("refs/heads/0.1"), filters));
        // regex
        filters.setBranches(List.of());
        filters.setTags(List.of("/0\\.1/"));
        assertTrue(DockstoreYamlHelper.filterGitReference(Path.of("refs/tags/0.1"), filters));
        assertFalse(DockstoreYamlHelper.filterGitReference(Path.of("refs/tags/foo/0.1"), filters));
        assertFalse(DockstoreYamlHelper.filterGitReference(Path.of("refs/tags/0.1/foo"), filters));
        assertFalse(DockstoreYamlHelper.filterGitReference(Path.of("refs/heads/0.1"), filters));

        // Match any feature/** branch and ALL tags
        // glob/regex combo
        filters.setBranches(List.of("feature/**"));
        filters.setTags(List.of("/.*/"));
        assertFalse(DockstoreYamlHelper.filterGitReference(Path.of("refs/heads/feature"), filters));
        assertTrue(DockstoreYamlHelper.filterGitReference(Path.of("refs/heads/feature/1234"), filters));
        assertTrue(DockstoreYamlHelper.filterGitReference(Path.of("refs/heads/feature/1234/filters"), filters));
        assertFalse(DockstoreYamlHelper.filterGitReference(Path.of("refs/heads/foo/feature/1234"), filters));
        assertFalse(DockstoreYamlHelper.filterGitReference(Path.of("refs/heads/featurefoo"), filters));
        assertTrue(DockstoreYamlHelper.filterGitReference(Path.of("refs/tags/anything"), filters));
        assertTrue(DockstoreYamlHelper.filterGitReference(Path.of("refs/tags/any/thing"), filters));

        // X.X.X semantic version tags with regex
        filters.setBranches(List.of());
        filters.setTags(List.of("/\\d+\\.\\d+\\.\\d+/"));
        assertTrue(DockstoreYamlHelper.filterGitReference(Path.of("refs/tags/0.0.0"), filters));
        assertTrue(DockstoreYamlHelper.filterGitReference(Path.of("refs/tags/1.10.0"), filters));
        assertFalse(DockstoreYamlHelper.filterGitReference(Path.of("refs/tags/1.10.0-beta"), filters));
        assertFalse(DockstoreYamlHelper.filterGitReference(Path.of("refs/tags/a.b.c"), filters));
        assertFalse(DockstoreYamlHelper.filterGitReference(Path.of("refs/tags/1.0."), filters));
        assertFalse(DockstoreYamlHelper.filterGitReference(Path.of("refs/heads/0.0.0"), filters));

        // Invalid reference throws an error (this should never happen)
        assertThrows(UnsupportedOperationException.class, () -> DockstoreYamlHelper.filterGitReference(Path.of("fake/reference"), filters));

        // Invalid glob does not match (logs a warning)
        filters.setTags(List.of("["));
        assertFalse(DockstoreYamlHelper.filterGitReference(Path.of("refs/tags/["), filters));

        // Invalid regex does not match (logs a warning)
        filters.setTags(List.of("/[/"));
        assertFalse(DockstoreYamlHelper.filterGitReference(Path.of("refs/tags/["), filters));
    }


    @Test
    void testGetSuggestedDockstoreYamlProperty() {
        Class dockstoreYamlClass = DockstoreYaml12.class;

        String suggestedProperty = DockstoreYamlHelper.getSuggestedDockstoreYamlProperty(dockstoreYamlClass, "z");
        assertEquals("", suggestedProperty, "Shouldn't suggest a property if there's too many changes that needs to be done");

        suggestedProperty = DockstoreYamlHelper.getSuggestedDockstoreYamlProperty(dockstoreYamlClass, "workflows");
        assertEquals("workflows", suggestedProperty, "Should return the same property back if it's already a valid property");

        suggestedProperty = DockstoreYamlHelper.getSuggestedDockstoreYamlProperty(dockstoreYamlClass, "affilliation");
        assertEquals("affiliation", suggestedProperty);

        suggestedProperty = DockstoreYamlHelper.getSuggestedDockstoreYamlProperty(dockstoreYamlClass, "filter");
        assertEquals("filters", suggestedProperty);

        suggestedProperty = DockstoreYamlHelper.getSuggestedDockstoreYamlProperty(dockstoreYamlClass, "apptool");
        assertEquals("tools", suggestedProperty);

        suggestedProperty = DockstoreYamlHelper.getSuggestedDockstoreYamlProperty(dockstoreYamlClass, "e-mail");
        assertEquals("email", suggestedProperty);

        suggestedProperty = DockstoreYamlHelper.getSuggestedDockstoreYamlProperty(dockstoreYamlClass, "testParameterFilets");
        assertEquals("testParameterFiles", suggestedProperty);
    }

    @Test
    void testGetDockstoreYamlProperties() {
        Set<String> properties = DockstoreYamlHelper.getDockstoreYamlProperties(DockstoreYaml12.class);
        assertTrue(39 <= properties.size(), "Should have the correct number of unique properties for a version 1.2 .dockstore.yml");

        properties = DockstoreYamlHelper.getDockstoreYamlProperties(DockstoreYaml11.class);
        assertTrue(29 <= properties.size(), "Should have the correct number of unique properties for a version 1.1 .dockstore.yml");
    }

    @Test
    void testValidateDockstoreYamlProperties() {
        try {
            DockstoreYamlHelper.validateDockstoreYamlProperties(DOCKSTORE12_YAML.replace("publish", "published"));
            fail("Should not pass property validation because there's an unknown property");
        } catch (DockstoreYamlHelper.DockstoreYamlException ex) {
            assertTrue(ex.getMessage().contains(DockstoreYamlHelper.UNKNOWN_PROPERTY));
        }

        try {
            DockstoreYamlHelper.validateDockstoreYamlProperties(DOCKSTORE11_YAML.replace("description", "descriptions"));
            fail("Should not pass property validation because there's an unknown property");
        } catch (DockstoreYamlHelper.DockstoreYamlException ex) {
            assertTrue(ex.getMessage().contains(DockstoreYamlHelper.UNKNOWN_PROPERTY));
        }
    }

    @Test
    void testAuthorHasNameOrOrcid() throws DockstoreYamlHelper.DockstoreYamlException {
        // Original .dockstore.yml should validate correctly
        DockstoreYamlHelper.readDockstoreYaml(DOCKSTORE12_YAML, true);

        // Replace authors with an author that has either a name or an ORCID
        // Both should validate
        DockstoreYamlHelper.readDockstoreYaml(replaceAuthors(DOCKSTORE12_YAML, "- name: Mister Potato"), true);
        DockstoreYamlHelper.readDockstoreYaml(replaceAuthors(DOCKSTORE12_YAML, "- orcid: 0000-0001-2345-6789"), true);

        // Replace authors with an author that does not have a name or an ORCID
        // Should not validate
        try {
            DockstoreYamlHelper.readDockstoreYaml(replaceAuthors(DOCKSTORE12_YAML, "- affiliation: The Org"), true);
            fail("Should not pass property validation because an author has neither a name nor an orcid");
        } catch (DockstoreYamlHelper.DockstoreYamlException ex) {
            assertTrue(ex.getMessage().toLowerCase().contains("author"));
        }
    }

    private static String replaceAuthors(String text, String replacement) {
        return text.replaceFirst("(?s)- name: Denis.*?affiliation: UCSC", replacement);
    }

    @Test
    void testAuthorEmail() throws DockstoreYamlHelper.DockstoreYamlException {
        DockstoreYamlHelper.readDockstoreYaml(replaceOrcid(DOCKSTORE12_YAML, "email: test@test.com"), true);
        try {
            DockstoreYamlHelper.readDockstoreYaml(replaceOrcid(DOCKSTORE12_YAML, "email: bad"), true);
            fail("Should not pass property validation because the email address is invalid");
        } catch (DockstoreYamlHelper.DockstoreYamlException ex) {
            assertTrue(ex.getMessage().toLowerCase().contains("email"));
        }
    }

    private static String replaceOrcid(String text, String replacement) {
        return text.replaceFirst("orcid: [^ ]*?", replacement);
    }

    @Test
    void testWorkflowSubclass() throws DockstoreYamlHelper.DockstoreYamlException {
        DockstoreYamlHelper.readDockstoreYaml(DOCKSTORE12_YAML.replace("subclass: wdl", "subclass: WDL"), true);
        try {
            DockstoreYamlHelper.readDockstoreYaml(DOCKSTORE12_YAML.replace("subclass: wdl", "subclass: BogusWL"), true);
            fail("Should not pass property validation because the the workflow subclass is invalid");
        } catch (DockstoreYamlHelper.DockstoreYamlException ex) {
            assertTrue(ex.getMessage().toLowerCase().contains("subclass"));
        }
    }

    @Test
    void testScalarWhereListExpected() throws DockstoreYamlHelper.DockstoreYamlException {
        final String prelude = "version: 1.2\nworkflows:\n - primaryDescriptorPath: /abc.wdl\n   subclass: wdl\n   testParameterFiles: ";
        DockstoreYamlHelper.readDockstoreYaml(prelude + "[ /some/file.txt ]", true);
        try {
            DockstoreYamlHelper.readDockstoreYaml(prelude + "/some/file.txt", true);
            fail("Should not succeed because the `testParameterFiles` property should be a list");
        } catch (DockstoreYamlHelper.DockstoreYamlException ex) {
            assertTrue(ex.getMessage().contains(DockstoreYamlHelper.BETTER_NO_SINGLE_ARGUMENT_CONSTRUCTOR_YAML_EXCEPTION_MESSAGE));
        }
    }

    @Test
    void testEnableAutoDoisMustBeFalse() {
        try {
            DockstoreYamlHelper.readDockstoreYaml(DOCKSTORE12_YAML.replace("enableAutoDois: false", "enableAutoDois: true"), true);
            fail("Should not succeed because only false is allowed for enableAutoDois");
        } catch (DockstoreYamlHelper.DockstoreYamlException ex) {
            assertTrue(ex.getMessage().contains("must be false"));
        }
    }

    @Test
    void testDisableDoiGeneration() throws DockstoreYamlHelper.DockstoreYamlException {
        final DockstoreYaml12 dockstoreYaml12 = DockstoreYamlHelper.readAsDockstoreYaml12(DOCKSTORE12_YAML, true);
        assertEquals(1, dockstoreYaml12.getWorkflows().stream().map(YamlWorkflow::getEnableAutoDois).filter(Objects::nonNull).count(),
                "There should be 1 workflow with enableAutoDois = false ");
        assertEquals(1, dockstoreYaml12.getNotebooks().stream().map(YamlNotebook::getEnableAutoDois).filter(Objects::nonNull).count(),
                "There should be 1 notebook with enableAutoDois = false ");
    }
}
