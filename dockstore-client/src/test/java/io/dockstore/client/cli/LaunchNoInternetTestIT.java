package io.dockstore.client.cli;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import io.dockstore.common.Utilities;
import io.dropwizard.testing.ResourceHelpers;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.rules.ExpectedException;

import static org.junit.Assert.assertTrue;

/**
 * Tests CLI launching with image on filesystem instead of internet
 * In general, ubuntu:0118999881999119725...3 is the file that exists only on filesystem and hopefully never on internet
 * All tests will be trying to use that image
 *
 * @author gluu
 * @since 1.6.0
 */
public class LaunchNoInternetTestIT {
    private static String DOCKER_IMAGE_DIRECTORY;
    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();
    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();
    @Rule
    public final ExpectedSystemExit exit = ExpectedSystemExit.none();
    @Rule
    public ExpectedException thrown = ExpectedException.none();
    private static final File DESCRIPTOR_FILE = new File(ResourceHelpers.resourceFilePath("nonexistent_image/CWL/nonexistent_image.cwl"));
    private static final File YAML_FILE = new File(ResourceHelpers.resourceFilePath("echo-job.yml"));
    private static final File DOCKERFILE = new File(ResourceHelpers.resourceFilePath("nonexistent_image/Dockerfile"));
    private static final String FAKE_IMAGE_NAME = "dockstore.org/bashwithbinbash:0118999881999119725...3";

    /**
     * Downloading an image with bash (Nextflow needs it) and saving it on the filesystem as something weird that is unlikely to be on the internet
     * to make sure that the Dockstore CLI only uses the image from the filesystem
     *
     * @throws IOException  Something has gone terribly wrong with preparing the fake docker image
     */
    @BeforeClass
    public static void downloadCustomDockerImage() throws IOException {
        Utilities.executeCommand("docker build -f " + DOCKERFILE + " . -t " + FAKE_IMAGE_NAME, System.out, System.err);
        DOCKER_IMAGE_DIRECTORY = Files.createTempDirectory("docker_images").toAbsolutePath().toString();
        Utilities.executeCommand("docker save -o " + DOCKER_IMAGE_DIRECTORY + "/fakeImage " + FAKE_IMAGE_NAME, System.out, System.err);
    }

    @Before
    public void clearImage() {
        try {
            Utilities.executeCommand("docker rmi " + FAKE_IMAGE_NAME);
        } catch (Exception e) {
            // Don't care that it failed, it probably just didn't have the image loaded before
        }
    }

    /**
     * When Docker image directory is not specified
     */
    @Test
    public void directoryNotSpecified() {
        checkFailed();

        ArrayList<String> args = new ArrayList<String>() {{
            add("tool");
            add("launch");
            add("--local-entry");
            add(DESCRIPTOR_FILE.getAbsolutePath());
            add("--yaml");
            add(YAML_FILE.getAbsolutePath());
            add("--config");
            add(ResourceHelpers.resourceFilePath("config"));
        }};
        Client.main(args.toArray(new String[0]));
    }

    /**
     * Docker image directory specified but doesn't exist
     */
    @Test
    public void directorySpecifiedDoesNotExist() throws IOException {
        checkFailed();
        exit.checkAssertionAfterwards(() -> assertTrue(systemOutRule.getLog().contains("The specified Docker image directory not found:")));

        String toWrite = "docker-images = src/test/resources/nonexistent_image/docker_images/thisDirectoryShouldNotExist";
        File configPath = createTempFile(toWrite);
        if (configPath == null) {
            Assert.fail("Could create temp config file");
        }

        ArrayList<String> args = new ArrayList<String>() {{
            add("tool");
            add("launch");
            add("--local-entry");
            add(DESCRIPTOR_FILE.getAbsolutePath());
            add("--yaml");
            add(YAML_FILE.getAbsolutePath());
            add("--config");
            add(configPath.getAbsolutePath());
        }};
        Client.main(args.toArray(new String[0]));
    }

    /**
     * Docker image directory specified is actually a file
     */
    @Test
    public void directorySpecifiedButIsAFile() throws IOException {
        checkFailed();
        exit.checkAssertionAfterwards(() -> assertTrue(systemOutRule.getLog().contains("The specified Docker image directory is a file:")));

        String toWrite = "docker-images = " + DOCKER_IMAGE_DIRECTORY + "/fakeImage";
        File configPath = createTempFile(toWrite);
        if (configPath == null) {
            Assert.fail("Could create temp config file");
        }
        ArrayList<String> args = new ArrayList<String>() {{
            add("tool");
            add("launch");
            add("--local-entry");
            add(DESCRIPTOR_FILE.getAbsolutePath());
            add("--yaml");
            add(YAML_FILE.getAbsolutePath());
            add("--config");
            add(configPath.getAbsolutePath());
        }};
        Client.main(args.toArray(new String[0]));
    }

    /**
     * Docker image directory specified but has no files in there
     */
    @Test
    public void directorySpecifiedButNoImages() throws IOException {
        checkFailed();
        exit.checkAssertionAfterwards(
                () -> assertTrue(systemOutRule.getLog().contains("There are no files in the docker image directory")));

        Path emptyTestDirectory = createEmptyTestDirectory();
        if (emptyTestDirectory == null) {
            Assert.fail("Could not create empty temp directory");
        }
        String toWrite = "docker-images = " + emptyTestDirectory.toAbsolutePath();
        File configPath = createTempFile(toWrite);
        if (configPath == null) {
            Assert.fail("Could not create temp config file");
        }
        ArrayList<String> args = new ArrayList<String>() {{
            add("tool");
            add("launch");
            add("--local-entry");
            add(DESCRIPTOR_FILE.getAbsolutePath());
            add("--yaml");
            add(YAML_FILE.getAbsolutePath());
            add("--config");
            add(configPath.getAbsolutePath());
        }};
        Client.main(args.toArray(new String[0]));
    }

    /**
     * Everything correctly configured with CWL tool
     */
    @Test
    public void correctCWL() throws IOException {
        File descriptorFile = new File(ResourceHelpers.resourceFilePath("nonexistent_image/CWL/nonexistent_image.cwl"));
        String toWrite = "docker-images = " + DOCKER_IMAGE_DIRECTORY;
        File configPath = createTempFile(toWrite);
        if (configPath == null) {
            Assert.fail("Could create temp config file");
        }
        ArrayList<String> args = new ArrayList<String>() {{
            add("tool");
            add("launch");
            add("--local-entry");
            add(descriptorFile.getAbsolutePath());
            add("--yaml");
            add(YAML_FILE.getAbsolutePath());
            add("--config");
            add(configPath.getAbsolutePath());
        }};
        Client.main(args.toArray(new String[0]));
        Assert.assertTrue("Final process status was not success", systemOutRule.getLog().contains("Final process status is success"));
    }

    /**
     * Everything correctly configured with NFL workflow
     */
    @Test
    public void correctNFL() throws IOException {
        copyNFLFiles();
        File descriptorFile = new File(ResourceHelpers.resourceFilePath("nonexistent_image/NFL/nextflow.config"));
        File jsonFile = new File(ResourceHelpers.resourceFilePath("nextflow_rnatoy/test.json"));
        String toWrite = "docker-images = " + DOCKER_IMAGE_DIRECTORY;
        File configPath = createTempFile(toWrite);
        if (configPath == null) {
            Assert.fail("Could create temp config file");
        }
        ArrayList<String> args = new ArrayList<String>() {{
            add("workflow");
            add("launch");
            add("--local-entry");
            add(descriptorFile.getAbsolutePath());
            add("--json");
            add(jsonFile.getAbsolutePath());
            add("--config");
            add(configPath.getAbsolutePath());
        }};
        Client.main(args.toArray(new String[0]));
        Assert.assertTrue("Final process status was not success", systemOutRule.getLog().contains("Saving copy of NextFlow stdout to: "));
    }

    /**
     * Everything correctly configured with WDL workflow
     */
    @Test
    public void correctWDL() throws IOException {
        File descriptorFile = new File(ResourceHelpers.resourceFilePath("nonexistent_image/WDL/nonexistent_image.wdl"));
        File jsonFile = new File(ResourceHelpers.resourceFilePath("nonexistent_image/WDL/test.json"));
        String toWrite = "docker-images = " + DOCKER_IMAGE_DIRECTORY;
        File configPath = createTempFile(toWrite);
        if (configPath == null) {
            Assert.fail("Could create temp config file");
        }
        ArrayList<String> args = new ArrayList<String>() {{
            add("workflow");
            add("launch");
            add("--local-entry");
            add(descriptorFile.getAbsolutePath());
            add("--json");
            add(jsonFile.getAbsolutePath());
            add("--config");
            add(configPath.getAbsolutePath());
        }};
        Client.main(args.toArray(new String[0]));
        Assert.assertTrue("Final process status was not success", systemOutRule.getLog().contains("Output files left in place"));
    }

    /**
     * Create empty test directory because don't want to add empty directory to Git
     */
    private Path createEmptyTestDirectory() throws IOException {
        return Files.createTempDirectory("empty_docker_images");

    }

    /**
     * Create a temp config file to avoid add so many to Git
     *
     * @param contents Contents of the config file
     * @return The temp config file
     */
    private File createTempFile(String contents) throws IOException {
        File tmpFile = File.createTempFile("config", ".tmp");
        try (FileWriter writer = new FileWriter(tmpFile)) {
            writer.write(contents);
            return tmpFile;
        }
    }

    /**
     * Check that the launch failed with normal no image output
     */
    private void checkFailed() {
        exit.expectSystemExit();
        exit.checkAssertionAfterwards(() -> assertTrue(systemErrRule.getLog().contains(
                "Docker is required to run this tool: Command '['docker', 'pull', '" + FAKE_IMAGE_NAME
                        + "']' returned non-zero exit status 1")));
    }

    /**
     * Nextflow with Dockstore CLI requires main.nf to be at same directory of execution, copying file over
     *
     * @throws IOException  Something has gone terribly wrong with copying the Nextflow files
     */
    private void copyNFLFiles() throws IOException {
        File userDir = new File(System.getProperty("user.dir"));
        File testFileDirectory = new File("src/test/resources/nonexistent_image/NFL");
        FileUtils.copyDirectory(testFileDirectory, userDir);
    }
}
