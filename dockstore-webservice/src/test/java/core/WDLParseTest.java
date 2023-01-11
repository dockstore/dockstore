/*
 *    Copyright 2018 OICR
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
package core;

import static io.dockstore.webservice.languages.WDLHandler.DEFAULT_WDL_VERSION;
import static io.dockstore.webservice.languages.WDLHandler.ERROR_PARSING_WORKFLOW_RECURSIVE_LOCAL_IMPORT;
import static io.dockstore.webservice.languages.WDLHandler.ERROR_PARSING_WORKFLOW_YOU_MAY_HAVE_A_RECURSIVE_IMPORT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.VersionTypeValidation;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.Author;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Tag;
import io.dockstore.webservice.core.Validation;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.core.WorkflowVersion;
import io.dockstore.webservice.languages.LanguageHandlerFactory;
import io.dockstore.webservice.languages.LanguageHandlerInterface;
import io.dockstore.webservice.languages.WDLHandler;
import io.dropwizard.testing.ResourceHelpers;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;
import uk.org.webcompere.systemstubs.stream.output.NoopStream;

@ExtendWith(SystemStubsExtension.class)
class WDLParseTest {

    @SystemStub
    public final SystemOut systemOutRule = new SystemOut(new NoopStream());

    @SystemStub
    public final SystemErr systemErrRule = new SystemErr(new NoopStream());

    @Test
    void testWDLMetadataExample() throws IOException {
        String filePath = ResourceHelpers.resourceFilePath("metadata_example0.wdl");
        LanguageHandlerInterface sInterface = LanguageHandlerFactory.getInterface(DescriptorLanguage.FileType.DOCKSTORE_WDL);
        Version entry = sInterface
            .parseWorkflowContent(filePath, FileUtils.readFileToString(new File(filePath), StandardCharsets.UTF_8), new HashSet<>(), new Tag());
        assertTrue(entry.getAuthor().contains("Chip Stewart"), "incorrect author");
        assertTrue(entry.getEmail().contains("stewart@broadinstitute.org"), "incorrect email");
    }

    @Test
    void testWDLMetadataExampleWithMerge() throws IOException {
        String filePath = ResourceHelpers.resourceFilePath("metadata_example1.wdl");
        LanguageHandlerInterface sInterface = LanguageHandlerFactory.getInterface(DescriptorLanguage.FileType.DOCKSTORE_WDL);
        Version entry = sInterface
            .parseWorkflowContent(filePath, FileUtils.readFileToString(new File(filePath), StandardCharsets.UTF_8), new HashSet<>(), new Tag());
        Set<Author> authors = entry.getAuthors();
        assertEquals(3, authors.size());
        for (Author author : authors) {
            assertNull(author.getEmail());
        }
    }

    @Test
    void testWDLMetadataExampleWithWorkflowMeta() throws IOException {
        String filePath = ResourceHelpers.resourceFilePath("metadata_example2.wdl");
        LanguageHandlerInterface sInterface = LanguageHandlerFactory.getInterface(DescriptorLanguage.FileType.DOCKSTORE_WDL);
        Version entry = sInterface
            .parseWorkflowContent(filePath, FileUtils.readFileToString(new File(filePath), StandardCharsets.UTF_8), new HashSet<>(), new Tag());
        Set<Author> authors = entry.getAuthors();
        assertEquals(3, authors.size());
        Optional<Author> authorWithEmail = authors.stream().filter(author -> author.getName().equals("Mr. Foo")).findFirst();
        assertTrue(authorWithEmail.isPresent());
        assertEquals("foo@foo.com", authorWithEmail.get().getEmail());
        authors.stream().filter(author -> !author.getName().equals("Mr. Foo")).forEach(authorWithoutEmail -> assertNull(authorWithoutEmail.getEmail()));
        assertEquals("This is a cool workflow", entry.getDescription(), "incorrect description");
    }

    @Test
    void testRecursiveImportsMetadata() {
        try {
            WDLHandler.checkForRecursiveLocalImports(getSourceFile1().getContent(), getSourceFiles(), new HashSet<>(), "/");
            fail("Should have detected recursive local import");
        } catch (ParseException e) {
            assertEquals("Recursive local import detected: /first-import.wdl", e.getMessage());
        }
    }

    @Test
    void testHandlingVariousWorkflowVersions() throws IOException {
        String workflowVersion = "draft-3";
        assertEquals("draft-3", WDLHandler.enhanceSemanticVersionString(workflowVersion));
        workflowVersion = "1.0";
        assertEquals("1.0.0", WDLHandler.enhanceSemanticVersionString(workflowVersion));
        workflowVersion = "1.0.0";
        assertEquals("1.0.0", WDLHandler.enhanceSemanticVersionString(workflowVersion));
        workflowVersion = "1.0-alpha";
        assertEquals("1.0.0-alpha", WDLHandler.enhanceSemanticVersionString(workflowVersion));
        workflowVersion = "1.0-rc.2.5+build-2.0";
        assertEquals("1.0.0-rc.2.5+build-2.0", WDLHandler.enhanceSemanticVersionString(workflowVersion));

        workflowVersion = "3.60";
        assertEquals("3.60.0", WDLHandler.enhanceSemanticVersionString(workflowVersion));
        workflowVersion = "10.4.0";
        assertEquals("10.4.0", WDLHandler.enhanceSemanticVersionString(workflowVersion));
        workflowVersion = "30.0-alpha";
        assertEquals("30.0.0-alpha", WDLHandler.enhanceSemanticVersionString(workflowVersion));
        workflowVersion = "3.0-rc.1.0.0+build-1.0";
        assertEquals("3.0.0-rc.1.0.0+build-1.0", WDLHandler.enhanceSemanticVersionString(workflowVersion));


        workflowVersion = "draft-3";
        assertFalse(WDLHandler.versionIsGreaterThanCurrentlySupported(workflowVersion));
        workflowVersion = "1.0";
        assertFalse(WDLHandler.versionIsGreaterThanCurrentlySupported(workflowVersion));
        workflowVersion = "1.0.0";
        assertFalse(WDLHandler.versionIsGreaterThanCurrentlySupported(workflowVersion));
        workflowVersion = "1.0-alpha";
        assertFalse(WDLHandler.versionIsGreaterThanCurrentlySupported(workflowVersion));
        workflowVersion = "1.0-rc.2.5+build-2.0";
        assertFalse(WDLHandler.versionIsGreaterThanCurrentlySupported(workflowVersion));

        workflowVersion = "3.6";
        assertTrue(WDLHandler.versionIsGreaterThanCurrentlySupported(workflowVersion));
        workflowVersion = "1.4.0";
        assertTrue(WDLHandler.versionIsGreaterThanCurrentlySupported(workflowVersion));
        workflowVersion = "3.0-alpha";
        assertTrue(WDLHandler.versionIsGreaterThanCurrentlySupported(workflowVersion));
        workflowVersion = "3.0-rc.1.0+build-1.0";
        assertTrue(WDLHandler.versionIsGreaterThanCurrentlySupported(workflowVersion));


        workflowVersion = "";
        SourceFile srcFile = getSimpleWorkflowSourcefileWithVersion(workflowVersion);
        assertFalse(WDLHandler.getSemanticVersionString(srcFile.getContent()).isPresent());
        workflowVersion = "version";
        srcFile = getSimpleWorkflowSourcefileWithVersion(workflowVersion);
        assertFalse(WDLHandler.getSemanticVersionString(srcFile.getContent()).isPresent());
        workflowVersion = "version goat";
        srcFile = getSimpleWorkflowSourcefileWithVersion(workflowVersion);
        assertEquals("goat", WDLHandler.getSemanticVersionString(srcFile.getContent()).get());
        workflowVersion = "version draft-3";
        srcFile = getSimpleWorkflowSourcefileWithVersion(workflowVersion);
        assertEquals("draft-3", WDLHandler.getSemanticVersionString(srcFile.getContent()).get());
        workflowVersion = "version 1.0";
        srcFile = getSimpleWorkflowSourcefileWithVersion(workflowVersion);
        assertEquals("1.0", WDLHandler.getSemanticVersionString(srcFile.getContent()).get());
        workflowVersion = "version 1.0.0";
        srcFile = getSimpleWorkflowSourcefileWithVersion(workflowVersion);
        assertEquals("1.0.0", WDLHandler.getSemanticVersionString(srcFile.getContent()).get());
        workflowVersion = "version 1.0-alpha";
        srcFile = getSimpleWorkflowSourcefileWithVersion(workflowVersion);
        assertEquals("1.0-alpha", WDLHandler.getSemanticVersionString(srcFile.getContent()).get());
        workflowVersion = "version 1.0-rc.2.5+build-2.0";
        srcFile = getSimpleWorkflowSourcefileWithVersion(workflowVersion);
        assertEquals("1.0-rc.2.5+build-2.0", WDLHandler.getSemanticVersionString(srcFile.getContent()).get());

        workflowVersion = "version 3.6";
        srcFile = getSimpleWorkflowSourcefileWithVersion(workflowVersion);
        assertEquals("3.6", WDLHandler.getSemanticVersionString(srcFile.getContent()).get());
        workflowVersion = "version 1.4.0";
        srcFile = getSimpleWorkflowSourcefileWithVersion(workflowVersion);
        assertEquals("1.4.0", WDLHandler.getSemanticVersionString(srcFile.getContent()).get());
        workflowVersion = "version 3.0-alpha";
        srcFile = getSimpleWorkflowSourcefileWithVersion(workflowVersion);
        assertEquals("3.0-alpha", WDLHandler.getSemanticVersionString(srcFile.getContent()).get());
        workflowVersion = "version 3.0-rc.1.0+build-1.0";
        srcFile = getSimpleWorkflowSourcefileWithVersion(workflowVersion);
        assertEquals("3.0-rc.1.0+build-1.0", WDLHandler.getSemanticVersionString(srcFile.getContent()).get());

        workflowVersion = "version";
        srcFile = getSimpleWorkflowSourcefileWithVersion(workflowVersion);
        assertFalse(WDLHandler.getUnsupportedWDLVersionErrorString(srcFile.getContent()).isPresent());
        workflowVersion = "";
        srcFile = getSimpleWorkflowSourcefileWithVersion(workflowVersion);
        assertFalse(WDLHandler.getUnsupportedWDLVersionErrorString(srcFile.getContent()).isPresent());
        workflowVersion = "version 1.0";
        srcFile = getSimpleWorkflowSourcefileWithVersion(workflowVersion);
        assertFalse(WDLHandler.getUnsupportedWDLVersionErrorString(srcFile.getContent()).isPresent());
        workflowVersion = "version 3.6";
        srcFile = getSimpleWorkflowSourcefileWithVersion(workflowVersion);
        assertTrue(WDLHandler.getUnsupportedWDLVersionErrorString(srcFile.getContent())
                .get().contains("The version of this workflow is 3.6"));
    }

    @Test
    void testGetLanguageVersion() throws IOException {
        // Test valid 'version' fields
        String languageVersion = "version 1.0";
        SourceFile sourceFile = getSimpleWorkflowSourcefileWithVersion(languageVersion);
        assertEquals("1.0", WDLHandler.getLanguageVersion(sourceFile.getAbsolutePath(), Set.of(sourceFile)).get());

        languageVersion = "version 1.1";
        sourceFile = getSimpleWorkflowSourcefileWithVersion(languageVersion);
        assertEquals("1.1", WDLHandler.getLanguageVersion(sourceFile.getAbsolutePath(), Set.of(sourceFile)).get());

        languageVersion = ""; // No 'version' field specified
        sourceFile = getSimpleWorkflowSourcefileWithVersion(languageVersion);
        assertEquals(DEFAULT_WDL_VERSION, WDLHandler.getLanguageVersion(sourceFile.getAbsolutePath(), Set.of(sourceFile)).get());

        languageVersion = "# A comment can precede the version\nversion 1.0";
        sourceFile = getSimpleWorkflowSourcefileWithVersion(languageVersion);
        assertEquals("1.0", WDLHandler.getLanguageVersion(sourceFile.getAbsolutePath(), Set.of(sourceFile)).get());

        languageVersion = "version 1.0 # comment on the same line";
        sourceFile = getSimpleWorkflowSourcefileWithVersion(languageVersion);
        assertEquals("1.0", WDLHandler.getLanguageVersion(sourceFile.getAbsolutePath(), Set.of(sourceFile)).get());

        languageVersion = "  version 1.0"; // Leading whitespace before version
        sourceFile = getSimpleWorkflowSourcefileWithVersion(languageVersion);
        assertEquals("1.0", WDLHandler.getLanguageVersion(sourceFile.getAbsolutePath(), Set.of(sourceFile)).get());

        // Test invalid 'version' fields
        languageVersion = "version"; // User forgot to specify a numerical version
        sourceFile = getSimpleWorkflowSourcefileWithVersion(languageVersion);
        assertTrue(WDLHandler.getLanguageVersion(sourceFile.getAbsolutePath(), Set.of(sourceFile)).isEmpty());

        languageVersion = "version 1 0"; // User forgot to a '.'
        sourceFile = getSimpleWorkflowSourcefileWithVersion(languageVersion);
        assertTrue(WDLHandler.getLanguageVersion(sourceFile.getAbsolutePath(), Set.of(sourceFile)).isEmpty());

        languageVersion = "Version 1.0"; // Capitalize 'Version'
        sourceFile = getSimpleWorkflowSourcefileWithVersion(languageVersion);
        assertTrue(WDLHandler.getLanguageVersion(sourceFile.getAbsolutePath(), Set.of(sourceFile)).isEmpty());

        languageVersion = "vision 1.0"; // Misspelled version
        sourceFile = getSimpleWorkflowSourcefileWithVersion(languageVersion);
        assertTrue(WDLHandler.getLanguageVersion(sourceFile.getAbsolutePath(), Set.of(sourceFile)).isEmpty());

        // Test valid and invalid 'version' fields with a workflow containing syntax errors
        languageVersion = "version 1.0\n\nimport brokenbrokenbroken"; // A workflow with a valid version but syntax errors
        sourceFile = getSimpleWorkflowSourcefileWithVersion(languageVersion);
        assertEquals("1.0", WDLHandler.getLanguageVersion(sourceFile.getAbsolutePath(), Set.of(sourceFile)).get());

        languageVersion = "import brokenbrokenbroken"; // A workflow with no version and syntax errors
        sourceFile = getSimpleWorkflowSourcefileWithVersion(languageVersion);
        assertEquals(DEFAULT_WDL_VERSION, WDLHandler.getLanguageVersion(sourceFile.getAbsolutePath(), Set.of(sourceFile)).get());

        languageVersion = "version 1 0\n\nimport brokenbrokenbroken"; // A workflow with an invalid version and syntax errors
        sourceFile = getSimpleWorkflowSourcefileWithVersion(languageVersion);
        assertTrue(WDLHandler.getLanguageVersion(sourceFile.getAbsolutePath(), Set.of(sourceFile)).isEmpty());
    }

    private static SourceFile getSimpleWorkflowSourcefileWithVersion(String version) throws IOException {
        SourceFile sourceFile = new SourceFile();
        sourceFile.setAbsolutePath("/Dockstore.wdl");
        sourceFile.setPath("Dockstore.wdl");
        sourceFile.setContent(version + "\n" + "\n" + "workflow helloworld {\n" + "call hello_world\n" + "}\n" + "\n"
                + "task hello_world {\n" + "  command {echo hello world}\n" + "}\n");
        return sourceFile;
    }

    @Test
    void parseRecursiveWorkflowContent() {
        WDLHandler wdlHandler = new WDLHandler();
        WorkflowVersion version = new WorkflowVersion();
        wdlHandler.parseWorkflowContent(getSourceFile1().getAbsolutePath(), getSourceFile1().getContent(), getSourceFiles(), version);
        SortedSet<Validation> validations = version.getValidations();
        assertTrue(validations.first().getMessage().contains("Recursive local import detected: /first-import.wdl"));
    }

    private static SourceFile getSourceFile1() {
        SourceFile sourceFile1 = new SourceFile();
        sourceFile1.setAbsolutePath("/Dockstore.wdl");
        sourceFile1.setPath("Dockstore.wdl");
        sourceFile1.setContent("import \"first-import.wdl\" as first\n" + "task hello {\n" + "  String name\n" + "\n" + "  command {\n"
                + "    echo 'Hello ${name}!'\n" + "  }\n" + "  output {\n" + "    File response = stdout()\n" + "  }\n" + "}\n" + "\n"
                + "workflow test {\n" + "  call hello\n" + "  call first.hello\n" + "}");
        return sourceFile1;
    }

    private static SourceFile getSourceFile2() {
        SourceFile sourceFile2 = new SourceFile();
        sourceFile2.setAbsolutePath("/first-import.wdl");
        sourceFile2.setPath("first-import.wdl");
        sourceFile2.setContent("import \"Dockstore.wdl\" as second\n" + "task hello {\n" + "  String name\n" + "\n" + "  command {\n"
                + "    echo 'Hello ${name}!'\n" + "  }\n" + "  output {\n" + "    File response = stdout()\n" + "  }\n" + "}");
        return sourceFile2;
    }

    private static Set<SourceFile> getSourceFiles() {
        Set<SourceFile> sourceFiles = new HashSet<>();
        sourceFiles.add(getSourceFile1());
        sourceFiles.add(getSourceFile2());
        return sourceFiles;
    }


    /**
     * Tests that Dockstore can handle a workflow with locally recursive imports
     */
    @Test
    void testLocallyRecursiveImport() {
        String type = "workflow";
        File recursiveWDL = new File(ResourceHelpers.resourceFilePath("local-recursive-import/localrecursive.wdl"));
        String primaryDescriptorFilePath = "localrecursive.wdl";
        SourceFile sourceFile = new SourceFile();

        File recursiveimportWDL = new File(ResourceHelpers.resourceFilePath("local-recursive-import/first-import.wdl"));
        SourceFile importsourceFile = new SourceFile();

        try {
            sourceFile.setContent(FileUtils.readFileToString(recursiveWDL, StandardCharsets.UTF_8));
            sourceFile.setAbsolutePath("/localrecursive.wdl");
            sourceFile.setPath("localrecursive.wdl");
            sourceFile.setType(DescriptorLanguage.FileType.DOCKSTORE_WDL);

            importsourceFile.setContent(FileUtils.readFileToString(recursiveimportWDL, StandardCharsets.UTF_8));
            importsourceFile.setAbsolutePath("/first-import.wdl");
            importsourceFile.setPath("first-import.wdl");
            importsourceFile.setType(DescriptorLanguage.FileType.DOCKSTORE_WDL);

            Set<SourceFile> sourceFileSet = new HashSet<>();
            sourceFileSet.add(sourceFile);

            sourceFileSet.add(importsourceFile);

            WDLHandler wdlHandler = new WDLHandler();
            VersionTypeValidation validation = wdlHandler.validateEntrySet(sourceFileSet, primaryDescriptorFilePath, type);
            assertFalse(validation.isValid());
            assertTrue(validation.getMessage().values().stream()
                    .anyMatch(msg -> StringUtils.contains(msg, ERROR_PARSING_WORKFLOW_RECURSIVE_LOCAL_IMPORT)));
        } catch (IOException e) {
            fail();
        }
    }


    /**
     * Tests that Dockstore can handle a workflow with recursive imports
     */
    @Test
    void testRecursiveImport() {
        String type = "workflow";
        File recursiveWDL = new File(ResourceHelpers.resourceFilePath("recursive.wdl"));
        String primaryDescriptorFilePath = recursiveWDL.getAbsolutePath();
        SourceFile sourceFile = new SourceFile();
        VersionTypeValidation versionTypeValidation = null;
        try {
            sourceFile.setContent(FileUtils.readFileToString(recursiveWDL, StandardCharsets.UTF_8));
            sourceFile.setAbsolutePath(recursiveWDL.getAbsolutePath());
            sourceFile.setPath(recursiveWDL.getAbsolutePath());
            sourceFile.setType(DescriptorLanguage.FileType.DOCKSTORE_WDL);
            Set<SourceFile> sourceFileSet = new HashSet<>();
            sourceFileSet.add(sourceFile);
            WDLHandler wdlHandler = new WDLHandler();
            versionTypeValidation = wdlHandler.validateEntrySet(sourceFileSet, primaryDescriptorFilePath, type);
        } catch (IOException e) {
            fail();
        } catch (CustomWebApplicationException e) {
            assertTrue(StringUtils.contains(e.getErrorMessage(), ERROR_PARSING_WORKFLOW_YOU_MAY_HAVE_A_RECURSIVE_IMPORT));
        }
        assertTrue(StringUtils.contains(versionTypeValidation.getMessage().get(recursiveWDL.getAbsolutePath()), ERROR_PARSING_WORKFLOW_YOU_MAY_HAVE_A_RECURSIVE_IMPORT));

    }

    /**
     * Tests that Dockstore can handle a workflow with something that kinda looks recursive but isn't
     */
    @Test
    void testNotReallyRecursiveImport() {
        String type = "workflow";
        File recursiveWDL = new File(ResourceHelpers.resourceFilePath("not-really-recursive/not-really-recursive.wdl"));
        String primaryDescriptorFilePath = recursiveWDL.getAbsolutePath();
        SourceFile sourceFile = new SourceFile();
        try {
            sourceFile.setContent(FileUtils.readFileToString(recursiveWDL, StandardCharsets.UTF_8));
            sourceFile.setAbsolutePath(recursiveWDL.getAbsolutePath());
            sourceFile.setPath(recursiveWDL.getAbsolutePath());
            sourceFile.setType(DescriptorLanguage.FileType.DOCKSTORE_WDL);
            Set<SourceFile> sourceFileSet = new HashSet<>();
            sourceFileSet.add(sourceFile);
            WDLHandler wdlHandler = new WDLHandler();
            wdlHandler.validateEntrySet(sourceFileSet, primaryDescriptorFilePath, type);
        } catch (IOException | CustomWebApplicationException e) {
            fail();
        }
    }

    /**
     * Tests that Dockstore can handle a WDL 1.0 workflow using HTTP and map import
     * Error parsing will throw an exception, but with no error should just pass
     *
     * Also tests metadata in WDL 1.0 files
     */
    @Test
    void testDraft3Code() {
        String type = "workflow";
        File primaryWDL = new File(ResourceHelpers.resourceFilePath("importTesting.wdl"));
        File importedWDL = new File(ResourceHelpers.resourceFilePath("md5sum.wdl"));
        String primaryDescriptorFilePath = primaryWDL.getAbsolutePath();
        SourceFile sourceFile = new SourceFile();
        SourceFile importedFile = new SourceFile();
        try {
            sourceFile.setContent(FileUtils.readFileToString(primaryWDL, StandardCharsets.UTF_8));
            sourceFile.setAbsolutePath(primaryWDL.getAbsolutePath());
            sourceFile.setPath(primaryWDL.getAbsolutePath());
            sourceFile.setType(DescriptorLanguage.FileType.DOCKSTORE_WDL);

            importedFile.setContent(FileUtils.readFileToString(importedWDL, StandardCharsets.UTF_8));
            importedFile.setAbsolutePath(importedWDL.getAbsolutePath());
            importedFile.setPath("./md5sum.wdl");
            importedFile.setType(DescriptorLanguage.FileType.DOCKSTORE_WDL);

            Set<SourceFile> sourceFileSet = new HashSet<>();
            sourceFileSet.add(sourceFile);
            sourceFileSet.add(importedFile);

            WDLHandler wdlHandler = new WDLHandler();
            wdlHandler.validateEntrySet(sourceFileSet, primaryDescriptorFilePath, type);

            LanguageHandlerInterface sInterface = LanguageHandlerFactory.getInterface(DescriptorLanguage.FileType.DOCKSTORE_WDL);
            Version entry = sInterface
                    .parseWorkflowContent(primaryWDL.getAbsolutePath(), FileUtils.readFileToString(primaryWDL, StandardCharsets.UTF_8), sourceFileSet, new WorkflowVersion());
            assertEquals(1, entry.getAuthor().split(",").length, "incorrect author");
            assertEquals("foobar@foo.com", entry.getEmail(), "incorrect email");
            assertTrue(entry.getDescription().length() > 0, "incorrect description");
            assertEquals(1, entry.getDescriptorTypeVersions().size());
            assertEquals("1.0", entry.getDescriptorTypeVersions().get(0));
        } catch (Exception e) {
            fail("Should properly parse file and imports.");
        }
    }

    @Test
    void testGetDescriptorTypeVersions() {
        String type = "workflow";
        File primaryWDL = new File(ResourceHelpers.resourceFilePath("importTesting.wdl"));
        File importedWDL = new File(ResourceHelpers.resourceFilePath("md5sum.wdl"));
        String primaryDescriptorFilePath = primaryWDL.getAbsolutePath();
        SourceFile sourceFile = new SourceFile();
        SourceFile importedFile = new SourceFile();
        try {
            final String primaryWDLString = FileUtils.readFileToString(primaryWDL, StandardCharsets.UTF_8);
            sourceFile.setContent(primaryWDLString);
            sourceFile.setAbsolutePath(primaryWDL.getAbsolutePath());
            sourceFile.setPath(primaryWDL.getAbsolutePath());
            sourceFile.setType(DescriptorLanguage.FileType.DOCKSTORE_WDL);

            final String importedWDLString = FileUtils.readFileToString(importedWDL, StandardCharsets.UTF_8);
            importedFile.setContent(importedWDLString);
            importedFile.setAbsolutePath(importedWDL.getAbsolutePath());
            importedFile.setPath("./md5sum.wdl");
            importedFile.setType(DescriptorLanguage.FileType.DOCKSTORE_WDL);

            Set<SourceFile> sourceFileSet = new HashSet<>();
            sourceFileSet.add(sourceFile);
            sourceFileSet.add(importedFile);

            WDLHandler wdlHandler = new WDLHandler();
            wdlHandler.validateEntrySet(sourceFileSet, primaryDescriptorFilePath, type);

            LanguageHandlerInterface sInterface = LanguageHandlerFactory.getInterface(DescriptorLanguage.FileType.DOCKSTORE_WDL);
            Version entry = sInterface
                    .parseWorkflowContent(primaryWDL.getAbsolutePath(), FileUtils.readFileToString(primaryWDL, StandardCharsets.UTF_8), sourceFileSet, new WorkflowVersion());
            assertEquals(1, entry.getDescriptorTypeVersions().size());
            assertTrue(entry.getDescriptorTypeVersions().contains("1.0"));

            // Make the primary descriptor version 1.1 so the primary descriptor and imported descriptor have different language versions
            sourceFile.setContent(primaryWDLString.replace("version 1.0", "version 1.1"));
            entry = sInterface
                    .parseWorkflowContent(primaryWDL.getAbsolutePath(), sourceFile.getContent(), sourceFileSet, new WorkflowVersion());
            assertEquals(2, entry.getDescriptorTypeVersions().size(), "Should have two language versions");
            assertTrue(entry.getDescriptorTypeVersions().contains("1.0") && entry.getDescriptorTypeVersions().contains("1.1"));

            // Add a syntax error to the imported descriptor
            importedFile.setContent(importedWDLString.replace("version 1.0", "version 1.0\n\nimport brokenbrokenbroken"));
            entry = sInterface
                    .parseWorkflowContent(primaryWDL.getAbsolutePath(), sourceFile.getContent(), sourceFileSet, new WorkflowVersion());
            assertEquals(2, entry.getDescriptorTypeVersions().size(), "Should have two language versions");
            assertTrue(entry.getDescriptorTypeVersions().contains("1.0") && entry.getDescriptorTypeVersions().contains("1.1"));

            // Use an invalid 'version' for the imported file. The imported source file should not have a version set
            importedFile.setContent(importedWDLString.replace("version 1.0", "version 1 0"));
            entry = sInterface
                    .parseWorkflowContent(primaryWDL.getAbsolutePath(), sourceFile.getContent(), sourceFileSet, new WorkflowVersion());
            assertEquals(1, entry.getDescriptorTypeVersions().size(), "Should have one language version");
            assertTrue(entry.getDescriptorTypeVersions().contains("1.1"));
        } catch (Exception e) {
            fail("Should properly parse language versions.");
        }
    }
}
