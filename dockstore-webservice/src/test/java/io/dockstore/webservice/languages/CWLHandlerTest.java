package io.dockstore.webservice.languages;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.when;

import com.google.gson.Gson;
import io.dockstore.common.DescriptorLanguage.FileType;
import io.dockstore.common.DockerImageReference;
import io.dockstore.common.MuteForSuccessfulTests;
import io.dockstore.common.Registry;
import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.core.Author;
import io.dockstore.webservice.core.DescriptionSource;
import io.dockstore.webservice.core.FileFormat;
import io.dockstore.webservice.core.ParsedInformation;
import io.dockstore.webservice.core.SourceFile;
import io.dockstore.webservice.core.Tool;
import io.dockstore.webservice.core.Version;
import io.dockstore.webservice.core.WorkflowVersion;
import io.dockstore.webservice.helpers.SourceFileHelper;
import io.dockstore.webservice.jdbi.ToolDAO;
import io.dockstore.webservice.languages.CWLHandler.DescriptorInput;
import io.dockstore.webservice.languages.LanguageHandlerInterface.DockerSpecifier;
import io.dropwizard.testing.ResourceHelpers;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.apache.http.HttpStatus;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;

/**
 * Tests public methods in the CWLHandler file
 * @author gluu
 * @since 1.5.0
 */
@ExtendWith(SystemStubsExtension.class)
@ExtendWith(MuteForSuccessfulTests.class)
class CWLHandlerTest {

    @SystemStub
    public final SystemOut systemOut = new SystemOut();

    @SystemStub
    public final SystemErr systemErr = new SystemErr();

    private Set<String> toValues(Set<FileFormat> formats) {
        return formats.stream().map(FileFormat::getValue).collect(Collectors.toSet());
    }

    /**
     * Tests if the input and output file formats can be extracted from a CWL descriptor file
     * @throws Exception
     */
    @Test
    void getInputFileFormats() throws Exception {
        CWLHandler cwlHandler = new CWLHandler();
        String filePath = ResourceHelpers.resourceFilePath("metadata_example4.cwl");
        Set<FileFormat> inputs = cwlHandler.getFileFormats(FileUtils.readFileToString(new File(filePath), StandardCharsets.UTF_8), "inputs");
        assertEquals(Set.of("http://edamontology.org/format_2572"), toValues(inputs));
        Set<FileFormat> outputs = cwlHandler.getFileFormats(FileUtils.readFileToString(new File(filePath), StandardCharsets.UTF_8), "outputs");
        assertEquals(Set.of("http://edamontology.org/format_1964"), toValues(outputs));
    }

    @Test
    void getInputFileFormatsSpecifiedAsArray() throws Exception {
        CWLHandler cwlHandler = new CWLHandler();
        String filePath = ResourceHelpers.resourceFilePath("metadata_example5.cwl");
        Set<FileFormat> inputs = cwlHandler.getFileFormats(FileUtils.readFileToString(new File(filePath), StandardCharsets.UTF_8), "inputs");
        assertEquals(Set.of("http://edamontology.org/format_2572", "http://edamontology.org/format_2573"), toValues(inputs));
        Set<FileFormat> outputs = cwlHandler.getFileFormats(FileUtils.readFileToString(new File(filePath), StandardCharsets.UTF_8), "outputs");
        assertEquals(Set.of("http://edamontology.org/format_1964", "http://edamontology.org/format_1965"), toValues(outputs));
    }

    @Test
    void testDeterminingImageRegistry() {
        CWLHandler cwlHandler = new CWLHandler();
        assertEquals(Registry.DOCKER_HUB, cwlHandler.determineImageRegistry("python:2.7").get(), "Should be Docker Hub");
        assertEquals(Registry.DOCKER_HUB, cwlHandler.determineImageRegistry("debian:jessie").get(), "Should be Docker Hub");
        assertEquals(Registry.DOCKER_HUB, cwlHandler.determineImageRegistry("knowengdev/data_cleanup_pipeline:07_11_2017").get(), "Should be Docker Hub");
        assertTrue(cwlHandler.determineImageRegistry("knowengdev/data_cleanup_pipeline").isEmpty(), "Should be empty for no version being included");
        assertTrue(cwlHandler.determineImageRegistry("python:").isEmpty(), "Should be empty for no version being included");
        assertEquals(Registry.AMAZON_ECR, cwlHandler.determineImageRegistry("137112412989.dkr.ecr.us-east-1.amazonaws.com/amazonlinux:latest").get(), "Should be Amazon");
        assertEquals(Registry.AMAZON_ECR, cwlHandler.determineImageRegistry("public.ecr.aws/ubuntu/ubuntu:latest").get(), "Should be Amazon");
        assertTrue(cwlHandler.determineImageRegistry("gcr.io/project-id/image:tag").isEmpty(), "Should be empty, Google not supported yet");
        assertEquals(Registry.QUAY_IO, cwlHandler.determineImageRegistry("quay.io/ucsc_cgl/verifybamid:1.30.0").get(), "Should be Quay");
    }

    @Test
    void testDeterminingImageSpecifier() {
        assertEquals(DockerSpecifier.NO_TAG, LanguageHandlerInterface.determineImageSpecifier("quay.io/ucsc_cgl/verifybamid", DockerImageReference.LITERAL));
        assertEquals(DockerSpecifier.LATEST, LanguageHandlerInterface.determineImageSpecifier("quay.io/ucsc_cgl/verifybamid:latest", DockerImageReference.LITERAL));
        assertEquals(DockerSpecifier.TAG, LanguageHandlerInterface.determineImageSpecifier("quay.io/ucsc_cgl/verifybamid:1.30.0", DockerImageReference.LITERAL));
        assertEquals(DockerSpecifier.DIGEST, LanguageHandlerInterface.determineImageSpecifier(
                "quay.io/ucsc_cgl/verifybamid@sha256:05442f015018fb6a315b666f80d205e9ac066b3c53a217d5f791d22831ae98ac",
                DockerImageReference.LITERAL
        ));
        assertEquals(DockerSpecifier.PARAMETER, LanguageHandlerInterface.determineImageSpecifier("docker_image", DockerImageReference.DYNAMIC));
    }

    @Test
    void testURLHandler() {
        ParsedInformation parsedInformation = new ParsedInformation();
        CWLHandler.setImportsBasedOnMapValue(parsedInformation, "https://potato.com");
        assertTrue(parsedInformation.isHasHTTPImports());
        assertFalse(parsedInformation.isHasLocalImports());
        ParsedInformation parsedInformation2 = new ParsedInformation();
        CWLHandler.setImportsBasedOnMapValue(parsedInformation2, "http://potato.com");
        assertTrue(parsedInformation2.isHasHTTPImports());
        assertFalse(parsedInformation2.isHasLocalImports());
        ParsedInformation parsedInformation3 = new ParsedInformation();
        CWLHandler.setImportsBasedOnMapValue(parsedInformation3, "ftp://potato.com");
        assertFalse(parsedInformation3.isHasHTTPImports());
        assertTrue(parsedInformation3.isHasLocalImports());
        ParsedInformation parsedInformation4 = new ParsedInformation();
        CWLHandler.setImportsBasedOnMapValue(parsedInformation4, "potato.cwl");
        assertFalse(parsedInformation4.isHasHTTPImports());
        assertTrue(parsedInformation4.isHasLocalImports());
        ParsedInformation parsedInformation5 = new ParsedInformation();
        CWLHandler.setImportsBasedOnMapValue(parsedInformation5, "httppotato.cwl");
        assertFalse(parsedInformation5.isHasHTTPImports());
        assertTrue(parsedInformation5.isHasLocalImports());
    }

    @Test
    void testURLFromEntry() {
        final LanguageHandlerInterface handler = Mockito.mock(LanguageHandlerInterface.class, Mockito.CALLS_REAL_METHODS);
        final ToolDAO toolDAO = Mockito.mock(ToolDAO.class);

        // Cases that don't rely on toolDAO
        assertNull(handler.getURLFromEntry("", toolDAO, null));
        assertEquals("https://images.sbgenomics.com/foo/bar", handler.getURLFromEntry("images.sbgenomics.com/foo/bar", toolDAO, DockerSpecifier.NO_TAG));
        assertEquals("https://images.sbgenomics.com/foo/bar", handler.getURLFromEntry("images.sbgenomics.com/foo/bar:1", toolDAO, DockerSpecifier.TAG));

        assertEquals("https://hub.docker.com/_/foo", handler.getURLFromEntry("foo", toolDAO, DockerSpecifier.NO_TAG));
        assertEquals("https://hub.docker.com/_/foo", handler.getURLFromEntry("foo:1", toolDAO, DockerSpecifier.TAG));

        assertEquals("https://gallery.ecr.aws/foo/bar/test", handler.getURLFromEntry("public.ecr.aws/foo/bar/test", toolDAO, DockerSpecifier.NO_TAG));
        assertEquals("https://gallery.ecr.aws/foo/bar", handler.getURLFromEntry("public.ecr.aws/foo/bar:1", toolDAO, DockerSpecifier.TAG));
        assertEquals("https://gallery.ecr.aws/foo/bar", handler.getURLFromEntry("public.ecr.aws/foo/bar@sha256:123456789abc", toolDAO, DockerSpecifier.DIGEST));
        assertEquals("https://012345678912.dkr.ecr.us-east-1.amazonaws.com/foo/bar",
            handler.getURLFromEntry("012345678912.dkr.ecr.us-east-1.amazonaws.com/foo/bar", toolDAO, DockerSpecifier.NO_TAG));
        assertEquals("https://012345678912.dkr.ecr.us-east-1.amazonaws.com/foo",
            handler.getURLFromEntry("012345678912.dkr.ecr.us-east-1.amazonaws.com/foo:1", toolDAO, DockerSpecifier.TAG));
        assertEquals("https://012345678912.dkr.ecr.us-east-1.amazonaws.com/foo", handler.getURLFromEntry("012345678912.dkr.ecr.us-east-1.amazonaws.com/foo@sha256:123456789abc", toolDAO,
                DockerSpecifier.DIGEST));

        assertEquals("https://ghcr.io/foo/bar", handler.getURLFromEntry("ghcr.io/foo/bar", toolDAO, DockerSpecifier.NO_TAG));
        assertEquals("https://ghcr.io/foo/bar", handler.getURLFromEntry("ghcr.io/foo/bar:1", toolDAO, DockerSpecifier.TAG));
        assertEquals("https://ghcr.io/foo/bar", handler.getURLFromEntry("ghcr.io/foo/bar@sha256:123456789abc", toolDAO, DockerSpecifier.DIGEST));

        // When toolDAO.findAllByPath() returns null/empty
        when(toolDAO.findAllByPath(Mockito.anyString(), Mockito.anyBoolean())).thenReturn(null);
        assertEquals("https://quay.io/repository/foo/bar", handler.getURLFromEntry("quay.io/foo/bar", toolDAO, DockerSpecifier.NO_TAG));
        assertEquals("https://quay.io/repository/foo/bar", handler.getURLFromEntry("quay.io/foo/bar:1", toolDAO, DockerSpecifier.TAG));

        assertEquals("https://hub.docker.com/r/foo/bar", handler.getURLFromEntry("foo/bar", toolDAO, DockerSpecifier.NO_TAG));
        assertEquals("https://hub.docker.com/r/foo/bar", handler.getURLFromEntry("foo/bar:1", toolDAO, DockerSpecifier.TAG));

        assertEquals("https://gallery.ecr.aws/foo/bar", handler.getURLFromEntry("public.ecr.aws/foo/bar", toolDAO, DockerSpecifier.NO_TAG));
        assertEquals("https://gallery.ecr.aws/foo/bar", handler.getURLFromEntry("public.ecr.aws/foo/bar:1", toolDAO, DockerSpecifier.TAG));
        assertEquals("https://gallery.ecr.aws/foo/bar", handler.getURLFromEntry("public.ecr.aws/foo/bar@sha256:123456789abc", toolDAO, DockerSpecifier.DIGEST));
        assertEquals("https://012345678912.dkr.ecr.us-east-1.amazonaws.com/foo",
            handler.getURLFromEntry("012345678912.dkr.ecr.us-east-1.amazonaws.com/foo", toolDAO, DockerSpecifier.NO_TAG));
        assertEquals("https://012345678912.dkr.ecr.us-east-1.amazonaws.com/foo",
            handler.getURLFromEntry("012345678912.dkr.ecr.us-east-1.amazonaws.com/foo:1", toolDAO, DockerSpecifier.TAG));
        assertEquals("https://012345678912.dkr.ecr.us-east-1.amazonaws.com/foo", handler.getURLFromEntry("012345678912.dkr.ecr.us-east-1.amazonaws.com/foo@sha256:123456789abc", toolDAO,
                DockerSpecifier.DIGEST));

        assertEquals("https://ghcr.io/foo/bar", handler.getURLFromEntry("ghcr.io/foo/bar", toolDAO, DockerSpecifier.NO_TAG));
        assertEquals("https://ghcr.io/foo/bar", handler.getURLFromEntry("ghcr.io/foo/bar:1", toolDAO, DockerSpecifier.TAG));
        assertEquals("https://ghcr.io/foo/bar", handler.getURLFromEntry("ghcr.io/foo/bar@sha256:123456789abc", toolDAO, DockerSpecifier.DIGEST));
        // A specific architecture image is referenced by digest but it may also include a tag from the multi-arch image
        assertEquals("https://ghcr.io/foo/bar", handler.getURLFromEntry("ghcr.io/foo/bar:1@sha256:123456789abc", toolDAO, DockerSpecifier.DIGEST));

        // When toolDAO.findAllByPath() returns non-empty List<Tool>
        when(toolDAO.findAllByPath(Mockito.anyString(), Mockito.anyBoolean())).thenReturn(List.of(Mockito.mock(Tool.class)));
        assertEquals("https://www.dockstore.org/containers/quay.io/foo/bar", handler.getURLFromEntry("quay.io/foo/bar", toolDAO, DockerSpecifier.NO_TAG));
        assertEquals("https://www.dockstore.org/containers/quay.io/foo/bar", handler.getURLFromEntry("quay.io/foo/bar:1", toolDAO, DockerSpecifier.TAG));

        assertEquals("https://www.dockstore.org/containers/registry.hub.docker.com/foo/bar", handler.getURLFromEntry("foo/bar", toolDAO, DockerSpecifier.NO_TAG));
        assertEquals("https://www.dockstore.org/containers/registry.hub.docker.com/foo/bar", handler.getURLFromEntry("foo/bar:1", toolDAO, DockerSpecifier.TAG));

        assertEquals("https://www.dockstore.org/containers/public.ecr.aws/foo/bar", handler.getURLFromEntry("public.ecr.aws/foo/bar", toolDAO, DockerSpecifier.NO_TAG));
        assertEquals("https://www.dockstore.org/containers/public.ecr.aws/foo/bar", handler.getURLFromEntry("public.ecr.aws/foo/bar:1", toolDAO, DockerSpecifier.TAG));
        assertEquals("https://www.dockstore.org/containers/public.ecr.aws/foo/bar", handler.getURLFromEntry("public.ecr.aws/foo/bar@sha256:123456789", toolDAO, DockerSpecifier.DIGEST));
        assertEquals("https://www.dockstore.org/containers/012345678912.dkr.ecr.us-east-1.amazonaws.com/foo",
            handler.getURLFromEntry("012345678912.dkr.ecr.us-east-1.amazonaws.com/foo", toolDAO, DockerSpecifier.NO_TAG));
        assertEquals("https://www.dockstore.org/containers/012345678912.dkr.ecr.us-east-1.amazonaws.com/foo",
            handler.getURLFromEntry("012345678912.dkr.ecr.us-east-1.amazonaws.com/foo:1", toolDAO, DockerSpecifier.TAG));
        assertEquals("https://www.dockstore.org/containers/012345678912.dkr.ecr.us-east-1.amazonaws.com/foo",
            handler.getURLFromEntry("012345678912.dkr.ecr.us-east-1.amazonaws.com/foo@sha256:123456789", toolDAO,
                    DockerSpecifier.DIGEST));

        assertEquals("https://www.dockstore.org/containers/ghcr.io/foo/bar", handler.getURLFromEntry("ghcr.io/foo/bar", toolDAO, DockerSpecifier.NO_TAG));
        assertEquals("https://www.dockstore.org/containers/ghcr.io/foo/bar", handler.getURLFromEntry("ghcr.io/foo/bar:1", toolDAO, DockerSpecifier.TAG));
        assertEquals("https://www.dockstore.org/containers/ghcr.io/foo/bar", handler.getURLFromEntry("ghcr.io/foo/bar@sha256:123456789abc", toolDAO, DockerSpecifier.DIGEST));
    }

    @Test
    void testGetImageNameWithoutSpecifier() {
        final LanguageHandlerInterface handler = Mockito.mock(LanguageHandlerInterface.class, Mockito.CALLS_REAL_METHODS);

        assertEquals("foo/bar", handler.getImageNameWithoutSpecifier("foo/bar", DockerSpecifier.NO_TAG));
        assertEquals("foo/bar", handler.getImageNameWithoutSpecifier("foo/bar:1", DockerSpecifier.TAG));
        assertEquals("foo/bar", handler.getImageNameWithoutSpecifier("foo/bar@sha256:123456789abc", DockerSpecifier.DIGEST));
        assertEquals("quay.io/foo/bar", handler.getImageNameWithoutSpecifier("quay.io/foo/bar:1", DockerSpecifier.TAG));
        assertEquals("ghcr.io/foo/bar", handler.getImageNameWithoutSpecifier("ghcr.io/foo/bar:1@sha256:123456789abc", DockerSpecifier.DIGEST));
        assertEquals("public.ecr.aws/foo/bar", handler.getImageNameWithoutSpecifier("public.ecr.aws/foo/bar@sha256:123456789abc", DockerSpecifier.DIGEST));
    }

    @Test
    void testGetRepositoryName() {
        final LanguageHandlerInterface handler = Mockito.mock(LanguageHandlerInterface.class, Mockito.CALLS_REAL_METHODS);

        assertEquals("foo/bar", handler.getRepositoryName(Registry.QUAY_IO, "quay.io/foo/bar:1", DockerSpecifier.TAG));
        assertEquals("foo/bar", handler.getRepositoryName(Registry.QUAY_IO, "quay.io/foo/bar@sha256:123456789abc", DockerSpecifier.DIGEST));

        assertEquals("library/foo", handler.getRepositoryName(Registry.DOCKER_HUB, "foo:1", DockerSpecifier.TAG));
        assertEquals("foo/bar", handler.getRepositoryName(Registry.DOCKER_HUB, "foo/bar:1", DockerSpecifier.TAG));
        assertEquals("foo/bar", handler.getRepositoryName(Registry.DOCKER_HUB, "foo/bar@sha256:123456789abc", DockerSpecifier.DIGEST));

        assertEquals("foo/bar", handler.getRepositoryName(Registry.GITHUB_CONTAINER_REGISTRY, "ghcr.io/foo/bar:1", DockerSpecifier.TAG));
        assertEquals("foo/bar/test", handler.getRepositoryName(Registry.GITHUB_CONTAINER_REGISTRY, "ghcr.io/foo/bar/test:1", DockerSpecifier.TAG));
        assertEquals("foo/bar", handler.getRepositoryName(Registry.GITHUB_CONTAINER_REGISTRY, "ghcr.io/foo/bar@sha256:123456789abc", DockerSpecifier.DIGEST));
        assertEquals("foo/bar", handler.getRepositoryName(Registry.GITHUB_CONTAINER_REGISTRY, "ghcr.io/foo/bar:1@sha256:123456789abc", DockerSpecifier.DIGEST));

        assertEquals("foo/bar", handler.getRepositoryName(Registry.AMAZON_ECR, "public.ecr.aws/foo/bar:1", DockerSpecifier.TAG));
        assertEquals("foo/bar/test", handler.getRepositoryName(Registry.AMAZON_ECR, "public.ecr.aws/foo/bar/test:1", DockerSpecifier.TAG));
        assertEquals("foo/bar", handler.getRepositoryName(Registry.AMAZON_ECR, "public.ecr.aws/foo/bar@sha256:123456789abc", DockerSpecifier.DIGEST));
        assertEquals("012345678912.dkr.ecr.us-east-1.amazonaws.com/foo/bar",
            handler.getRepositoryName(Registry.AMAZON_ECR, "012345678912.dkr.ecr.us-east-1.amazonaws.com/foo/bar:1", DockerSpecifier.TAG));
        assertEquals("012345678912.dkr.ecr.us-east-1.amazonaws.com/foo/bar",
            handler.getRepositoryName(Registry.AMAZON_ECR, "012345678912.dkr.ecr.us-east-1.amazonaws.com/foo/bar@sha256:123456789abc", DockerSpecifier.DIGEST));
    }

    @Test
    void testGetSpecifierName() {
        final LanguageHandlerInterface handler = Mockito.mock(LanguageHandlerInterface.class, Mockito.CALLS_REAL_METHODS);

        assertEquals("", handler.getSpecifierName("foo/bar", DockerSpecifier.NO_TAG));
        assertEquals("1", handler.getSpecifierName("foo/bar:1", DockerSpecifier.TAG));
        assertEquals("sha256:123456789abc", handler.getSpecifierName("foo@sha256:123456789abc", DockerSpecifier.DIGEST));
        assertEquals("sha256:123456789abc", handler.getSpecifierName("ghcr.io/foo/bar/test@sha256:123456789abc", DockerSpecifier.DIGEST));
        assertEquals("sha256:123456789abc", handler.getSpecifierName("ghcr.io/foo/bar/test:1@sha256:123456789abc", DockerSpecifier.DIGEST));
    }

    @Test
    void testGetContentJson() throws IOException {
        CWLHandler cwlHandler = new CWLHandler();

        // create and mock parameters for getContent()
        final Set<SourceFile> emptySet = Collections.emptySet();
        final ToolDAO toolDAO = Mockito.mock(ToolDAO.class);
        when(toolDAO.findAllByPath(Mockito.anyString(), Mockito.anyBoolean())).thenReturn(null);

        File cwlFile = new File(ResourceHelpers.resourceFilePath("json.cwl"));
        cwlHandler.getContent("/json.cwl", FileUtils.readFileToString(cwlFile, StandardCharsets.UTF_8), emptySet,
            LanguageHandlerInterface.Type.TOOLS, toolDAO);
    }

    @Test
    void testGetContentWithMalformedDescriptors() throws IOException {
        CWLHandler cwlHandler = new CWLHandler();

        // create and mock parameters for getContent()
        final Set<SourceFile> emptySet = Collections.emptySet();
        final ToolDAO toolDAO = Mockito.mock(ToolDAO.class);
        when(toolDAO.findAllByPath(Mockito.anyString(), Mockito.anyBoolean())).thenReturn(null);

        // expect parsing error
        File cwlFile = new File(ResourceHelpers.resourceFilePath("brokenCWL.cwl"));
        try {
            cwlHandler.getContent("/brokenCWL.cwl", FileUtils.readFileToString(cwlFile, StandardCharsets.UTF_8), emptySet,
                LanguageHandlerInterface.Type.TOOLS, toolDAO);
            fail("Expected parsing error");
        } catch (CustomWebApplicationException e) {
            assertEquals(HttpStatus.SC_UNPROCESSABLE_ENTITY, e.getResponse().getStatus());
            assertThat(e.getMessage()).contains(CWLHandler.CWL_PARSE_ERROR);

        }

        // expect error based on invalid cwlVersion
        cwlFile = new File(ResourceHelpers.resourceFilePath("badVersionCWL.cwl"));
        try {
            cwlHandler.getContent("/badVersionCWL.cwl", FileUtils.readFileToString(cwlFile, StandardCharsets.UTF_8), emptySet,
                LanguageHandlerInterface.Type.TOOLS, toolDAO);
            fail("Expected cwlVersion error");
        } catch (CustomWebApplicationException e) {
            assertEquals(HttpStatus.SC_UNPROCESSABLE_ENTITY, e.getResponse().getStatus());
            assertThat(e.getMessage()).contains(CWLHandler.CWL_VERSION_ERROR);
        }

        // expect error based on an undefined cwlVersion
        cwlFile = new File(ResourceHelpers.resourceFilePath("noVersionCWL.cwl"));
        try {
            cwlHandler.getContent("/noVersionCWL.cwl", FileUtils.readFileToString(cwlFile, StandardCharsets.UTF_8), emptySet,
                LanguageHandlerInterface.Type.TOOLS, toolDAO);
            fail("Expected undefined cwlVersion error");
        } catch (CustomWebApplicationException e) {
            assertEquals(HttpStatus.SC_UNPROCESSABLE_ENTITY, e.getResponse().getStatus());
            assertThat(e.getMessage()).contains(CWLHandler.CWL_NO_VERSION_ERROR);
        }

        // expect error based on invalid JSON $import/$include
        cwlFile = new File(ResourceHelpers.resourceFilePath("invalidMapCWL.cwl"));
        try {
            cwlHandler.getContent("/invalidMapCWL.cwl", FileUtils.readFileToString(cwlFile, StandardCharsets.UTF_8), emptySet,
                LanguageHandlerInterface.Type.TOOLS, toolDAO);
            fail("Expected parse error: value of workflow step run field should be a string, workflow, or tool");
        } catch (CustomWebApplicationException e) {
            assertEquals(HttpStatus.SC_UNPROCESSABLE_ENTITY, e.getResponse().getStatus());
            assertThat(e.getMessage()).contains(CWLHandler.CWL_PARSE_SECONDARY_ERROR);
        }
    }

    @Test
    void testPackedCwl() throws IOException {
        CWLHandler cwlHandler = new CWLHandler();
        final Set<SourceFile> emptySet = Collections.emptySet();
        final ToolDAO toolDAO = Mockito.mock(ToolDAO.class);
        when(toolDAO.findAllByPath(Mockito.anyString(), Mockito.anyBoolean())).thenReturn(null);

        File cwlFile = new File(ResourceHelpers.resourceFilePath("packed.cwl"));
        cwlHandler.getContent("/packed.cwl", FileUtils.readFileToString(cwlFile, StandardCharsets.UTF_8), emptySet,
            LanguageHandlerInterface.Type.TOOLS, toolDAO);

        final Version version = Mockito.mock(Version.class);
        Mockito.doNothing().when(version).setDescriptionAndDescriptionSource(Mockito.anyString(), Mockito.any(DescriptionSource.class));
        Mockito.doNothing().when(version).addAuthor(Mockito.any(Author.class));
        cwlHandler.parseWorkflowContent("/packed.cwl", FileUtils.readFileToString(cwlFile, StandardCharsets.UTF_8), emptySet, version);
        Mockito.verify(version, Mockito.atLeastOnce()).setDescriptionAndDescriptionSource(Mockito.anyString(), Mockito.any(DescriptionSource.class));
        Mockito.verify(version, Mockito.never()).addAuthor(Mockito.any(Author.class));
    }

    /**
     * Test the precedence of CWL requirements and hints.
     *
     * <p> The referenced workflow defines 9 workflow steps which enumerate the possible combinations
     * of a requirement, hint, or neither, at both the parent workflow step and child tool level.
     * The ID of each workflow step indicates the combination for that particular workflow step.
     * For example, the workflow step ID "requirement_none" means that the workflow step defines
     * requirement, and the invoked tool does not define a requirement or a hint.
     *
     * <p> According to the CWL spec, a child inherits requirements and hints from its parent.
     * Child requirements override parent requirements, child hints override parent hints, and if
     * both are present at a node, requirements take precedence over hints.
     */
    @Test
    void testCWLRequirementsAndHints() throws IOException {
        CWLHandler cwlHandler = new CWLHandler();

        // create and mock parameters for getContent()
        final String resourceRoot = "requirements-and-hints";
        final SourceFile parentFile = createSourceFile(resourceRoot, "/parent.cwl", FileType.DOCKSTORE_CWL);
        final Set<SourceFile> secondarySourceFiles = Set.of(
            createSourceFile(resourceRoot, "/requirement.cwl", FileType.DOCKSTORE_CWL),
            createSourceFile(resourceRoot, "/hint.cwl", FileType.DOCKSTORE_CWL),
            createSourceFile(resourceRoot, "/none.cwl", FileType.DOCKSTORE_CWL));
        final ToolDAO toolDAO = Mockito.mock(ToolDAO.class);

        // determine the tool information from the "repo"
        String tableToolContent = cwlHandler.getContent(parentFile.getAbsolutePath(), parentFile.getContent(),
            secondarySourceFiles, LanguageHandlerInterface.Type.TOOLS, toolDAO).get();

        // check that the dockerPulls are correct
        Gson gson = new Gson();
        List<Map<String, String>> tools = gson.fromJson(tableToolContent, List.class);
        for (Map<String, String> tool: tools) {
            String id = tool.get("id");
            String docker = tool.get("docker");
            if (Set.of("requirement_none", "requirement_hint", "hint_none").contains(id)) {
                assertEquals("step", docker);
            } else {
                assertEquals("child", docker);
            }
        }
        assertEquals(3 * 3 - 1, tools.size(), "there should be a dockerPull for all workflow steps except the one with no requirements/hints");
    }

    private SourceFile createSourceFile(String resourceRoot, String sourceFilePath, FileType fileType) throws IOException {
        String content = FileUtils.readFileToString(new File(ResourceHelpers.resourceFilePath(resourceRoot + sourceFilePath)), StandardCharsets.UTF_8);
        final SourceFile sourceFile = new SourceFile();
        sourceFile.setPath(sourceFilePath);
        sourceFile.setAbsolutePath(sourceFilePath);
        sourceFile.setContent(content);
        sourceFile.setType(fileType);
        return sourceFile;
    }

    /**
     * Test a workflow with an ID that contains slashes.
     */
    @Test
    void testWorkflowWithIdThatContainsSlashes() throws IOException {
        CWLHandler cwlHandler = new CWLHandler();

        final String resourceRoot = "workflow-id-slashes";
        final SourceFile primaryFile = createSourceFile(resourceRoot, "/main.cwl", FileType.DOCKSTORE_CWL);
        final ToolDAO toolDAO = Mockito.mock(ToolDAO.class);

        String toolTableContent = cwlHandler.getContent(primaryFile.getAbsolutePath(), primaryFile.getContent(), Set.of(
            createSourceFile(resourceRoot, "/tool.cwl", FileType.DOCKSTORE_CWL)), LanguageHandlerInterface.Type.TOOLS, toolDAO).get();
        assertTrue(toolTableContent.contains("step_name"), "step id should be part of tool table content");

        String dagContent = cwlHandler.getContent(primaryFile.getAbsolutePath(), primaryFile.getContent(), Set.of(), LanguageHandlerInterface.Type.DAG, toolDAO).get();
        assertTrue(dagContent.contains("step_name"), "step id should be part of dag content");
    }

    /**
     * Test that language versions are extracted from SourceFiles and set properly.
     */
    @Test
    void testLanguageVersionExtraction() throws IOException {
        CWLHandler cwlHandler = new CWLHandler();

        final String resourceRoot = "multi-version-cwl";
        final SourceFile manyFile = createSourceFile(resourceRoot, "/many-version.cwl", FileType.DOCKSTORE_CWL);
        final SourceFile oneFile = createSourceFile(resourceRoot, "/one-version.cwl", FileType.DOCKSTORE_CWL);
        final SourceFile noFile = createSourceFile(resourceRoot, "/no-version.cwl", FileType.DOCKSTORE_CWL);

        // Test multiple files containing many versions.
        final Version version = new WorkflowVersion();
        List.of(manyFile, oneFile, noFile).forEach(sourceFile -> sourceFile.getMetadata().setTypeVersion("bogus"));
        cwlHandler.parseWorkflowContent(manyFile.getPath(), manyFile.getContent(), Set.of(manyFile, oneFile, noFile), version);
        assertEquals(List.of("v1.2", "v1.1", "v1.0"), version.getVersionMetadata().getDescriptorTypeVersions());
        assertEquals("v1.2", manyFile.getMetadata().getTypeVersion());
        assertEquals("v1.1", oneFile.getMetadata().getTypeVersion());
        assertEquals(null, noFile.getMetadata().getTypeVersion());

        // Test one file containing no versions.
        noFile.getMetadata().setTypeVersion(null); // Reset
        version.getVersionMetadata().setDescriptorTypeVersions(List.of("bogus"));
        cwlHandler.parseWorkflowContent(noFile.getPath(), noFile.getContent(), Set.of(noFile), version);
        assertEquals(null, noFile.getMetadata().getTypeVersion());
        assertEquals(List.of(), version.getVersionMetadata().getDescriptorTypeVersions());
    }

    @Test
    void testFileInputs() throws IOException {
        final CWLHandler cwlHandler = new CWLHandler();
        final String sourceFilePath = "metadata_example0.cwl";
        final SourceFile sourceFile = createSourceFile("", sourceFilePath, FileType.DOCKSTORE_CWL);
        final WorkflowVersion workflowVersion = new WorkflowVersion();
        workflowVersion.addSourceFile(sourceFile);
        workflowVersion.setWorkflowPath(sourceFilePath);
        workflowVersion.setValid(true);
        final List<DescriptorInput> fileInputs = cwlHandler.getFileInputs(workflowVersion).get();
        assertEquals(List.of("tumor", "refFrom", "bbFrom", "normal"), fileInputs.stream().map(
            DescriptorInput::name).toList());
    }

    @Test
    void testArrayFileInputs() throws IOException {
        final CWLHandler cwlHandler = new CWLHandler();
        final String sourceFilePath = "array-inputs.cwl";
        final SourceFile sourceFile = createSourceFile("", sourceFilePath, FileType.DOCKSTORE_CWL);
        final WorkflowVersion workflowVersion = new WorkflowVersion();
        workflowVersion.addSourceFile(sourceFile);
        workflowVersion.setWorkflowPath(sourceFilePath);
        workflowVersion.setValid(true);
        final List<DescriptorInput> fileInputs = cwlHandler.getFileInputs(workflowVersion).get();
        assertEquals(List.of("filesA", "filesB", "filesC"), fileInputs.stream().map(
            DescriptorInput::name).toList());
    }

    @Test
    void testFileInputWithoutType() {
        final String cwl = """
        cwlVersion: v1.0
        class: Workflow
                
        inputs:
          bam_cram_file: File
                
        outputs:
          output_file:
            type: File
            outputSource: hello-world/output
                
        steps:
          hello-world:
            run: dockstore-tool-helloworld.cwl
            in:
              input_file: bam_cram_file
            out: [output]

            """;
        final CWLHandler cwlHandler = new CWLHandler();
        final SourceFile sourceFile = new SourceFile();
        final String sourceFilePath = "/Dockstore.cwl";
        sourceFile.setPath(sourceFilePath);
        sourceFile.setAbsolutePath(sourceFilePath);
        sourceFile.setContent(cwl);
        final WorkflowVersion workflowVersion = new WorkflowVersion();
        workflowVersion.addSourceFile(sourceFile);
        workflowVersion.setWorkflowPath(sourceFilePath);
        workflowVersion.setValid(true);
        final List<DescriptorInput> fileInputs = cwlHandler.getFileInputs(workflowVersion).get();
        assertEquals("bam_cram_file", fileInputs.get(0).name());
    }

    @Test
    void testPossibleUrls() throws IOException {
        final CWLHandler cwlHandler = new CWLHandler();
        final SourceFile testSourceFile =
            createSourceFile("", "topmed_freeze_calling.json", FileType.CWL_TEST_JSON);
        final Optional<JSONObject> jsonObject =
            SourceFileHelper.testFileAsJsonObject(testSourceFile);
        final List<String> possibleUrls = cwlHandler.possibleUrlsFromTestParameterFile(jsonObject.get());
        assertEquals(8, possibleUrls.size());
    }

    @Test
    void testCanHandleNanAndInf() throws IOException {
        final String cwl = """
        cwlVersion: v1.0
        class: Workflow
        inputs:
          nan_input: {default: .nan, id: nan_input, type: float}
          inf_input: {default: .inf, id: inf_input, type: float}
        outputs:
        steps:
            """;
        final CWLHandler cwlHandler = new CWLHandler();
        final Set<SourceFile> emptySet = Collections.emptySet();
        final ToolDAO toolDAO = Mockito.mock(ToolDAO.class);
        cwlHandler.getContent("/main.cwl", cwl, emptySet, LanguageHandlerInterface.Type.TOOLS, toolDAO);
    }
}
