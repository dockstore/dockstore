package io.dockstore.client.cli;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import io.dockstore.common.Utilities;
import io.dropwizard.testing.ResourceHelpers;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.rules.ExpectedException;

import static org.junit.Assert.assertTrue;

/**
 * @author gluu
 * @since 1.6.0
 */
public class LaunchNoInternetTestIT {
    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog();

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog();

    @Rule
    public final ExpectedSystemExit exit = ExpectedSystemExit.none();

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private static final File DESCRIPTOR_FILE = new File(ResourceHelpers.resourceFilePath("nonexistent_image/nonexistent_image.cwl"));
    private static final File JSON_FILE = new File(ResourceHelpers.resourceFilePath("echo-job.yml"));

    /**
     * Test that launches a tool that only exists on the file system and not the internet
     * If no docker-image directory is specified, launch should fail because no internet
     */
    @Test
    public void launchImageFromFileNotSpecified() {
        clearImage();
        checkFailed();

        ArrayList<String> args = new ArrayList<String>() {{
            add("tool");
            add("launch");
            add("--local-entry");
            add(DESCRIPTOR_FILE.getAbsolutePath());
            add("--yaml");
            add(JSON_FILE.getAbsolutePath());
        }};
        Client.main(args.toArray(new String[0]));
    }

    /**
     * Test that launches a tool that only exists on the file system and not the internet
     * If the docker-image directory is doesn't exist, say that it doesn't exist and launch should fail because no internet
     */
    @Test
    public void launchImageFromFileNoSuchFile() {
        clearImage();
        checkFailed();
        exit.checkAssertionAfterwards(()-> assertTrue(systemOutRule.getLog().contains("Directory not found:")));

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
            add(JSON_FILE.getAbsolutePath());
            add("--config");
            add(configPath.getAbsolutePath());
        }};
        Client.main(args.toArray(new String[0]));
    }

    /**
     * Test that launches a tool that only exists on the file system and not the internet
     * If the docker-image directory is actually a file, say it and launch should fail because no internet
     */
    @Test
    public void launchImageFromFileNotADirectory() {
        clearImage();
        checkFailed();
        exit.checkAssertionAfterwards(()-> assertTrue(systemOutRule.getLog().contains("Directory is a file:")));

        String toWrite = "docker-images = src/test/resources/nonexistent_image/docker_images/fakeImage";
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
            add(JSON_FILE.getAbsolutePath());
            add("--config");
            add(configPath.getAbsolutePath());
        }};
        Client.main(args.toArray(new String[0]));
    }

    /**
     * Test that launches a tool that only exists on the file system and not the internet
     * If the docker-image directory is specified but there aren't any files, say it and launch should fail because no internet
     */
    @Test
    public void launchImageFromFileNoImages() {
        clearImage();
        checkFailed();
        exit.checkAssertionAfterwards(()-> assertTrue(systemOutRule.getLog().contains("There are no files in the docker image directory")));

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
            add(JSON_FILE.getAbsolutePath());
            add("--config");
            add(configPath.getAbsolutePath());
        }};
        Client.main(args.toArray(new String[0]));
    }

    /**
     * Test that launches a tool that only exists on the file system and not the internet
     * If the docker-image directory is specified and is correct, say that it doesn't exist and launch should fail because no internet
     */
    @Test
    public void launchImageFromFileCorrect() {
        clearImage();
        File descriptorFile = new File(ResourceHelpers.resourceFilePath("nonexistent_image/nonexistent_image.cwl"));
        String toWrite = "docker-images = src/test/resources/nonexistent_image/docker_images";
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
            add(JSON_FILE.getAbsolutePath());
            add("--config");
            add(configPath.getAbsolutePath());
        }};
        Client.main(args.toArray(new String[0]));
        Assert.assertTrue("Final process status was not success", systemOutRule.getLog().contains("Final process status is success"));
    }

    /**
     * Remove the image just in case Travis already has it from another test
     */
    private void clearImage() {
        try {
            Utilities.executeCommand("docker rmi alpine:0118999881999119725...3");
        } catch (Exception e) {
            // Don't care that it failed, it probably just didn't have the image loaded before
        }
    }

    /**
     * Create empty test directory because don't want to add empty directory to Git
     */
    private Path createEmptyTestDirectory() {
        try {
            return Files.createTempDirectory("empty_docker_images");
        } catch (IOException e) {
            // Something has gone horribly wrong with the test
        }
        return null;
    }

    /**
     * Create a temp config file to avoid add so many to Git
     *
     * @param contents Contents of the config file
     * @return The temp config file
     */
    private File createTempFile(String contents) {
        try {
            File tmpFile = File.createTempFile("config", ".tmp");
            FileWriter writer = new FileWriter(tmpFile);
            writer.write(contents);
            writer.close();
            return tmpFile;
        } catch (IOException e) {
            // Something has gone horribly wrong with the test
        }
        return null;
    }

    /**
     * Check that the launch failed with normal no image output
     */
    private void checkFailed() {
        exit.expectSystemExit();
        exit.checkAssertionAfterwards(() -> assertTrue(systemErrRule.getLog().contains(
                "Error response from daemon: manifest for alpine:0118999881999119725...3 not found")));
    }
}
