package io.dockstore.client.cli.nested;

import java.io.File;

/**
 * This is a basic class that contains information about the local copy of the files used to run a tool/workflow
 */
public class LauncherFiles {
    private File workingDirectory;
    private File primaryDescriptor;
    private File zippedEntry;

    public LauncherFiles(File workingDirectory, File primaryDescriptor, File zippedEntry) {
        this.workingDirectory = workingDirectory;
        this.primaryDescriptor = primaryDescriptor;
        this.zippedEntry = zippedEntry;
    }

    public File getWorkingDirectory() {
        return workingDirectory;
    }

    public void setWorkingDirectory(File workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    public File getPrimaryDescriptor() {
        return primaryDescriptor;
    }

    public void setPrimaryDescriptor(File primaryDescriptor) {
        this.primaryDescriptor = primaryDescriptor;
    }

    public File getZippedEntry() {
        return zippedEntry;
    }

    public void setZippedEntry(File zippedEntry) {
        this.zippedEntry = zippedEntry;
    }
}
