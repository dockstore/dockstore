package io.dockstore.webservice.helpers;

import org.junit.Assert;
import org.junit.Test;

public class EntryVersionHelperTest {

    @Test
    public void removeWorkingDirectory() {
        Assert.assertEquals("Dockstore.cwl", EntryVersionHelper.removeWorkingDirectory("/Dockstore.cwl", "Dockstore.cwl"));
        Assert.assertEquals("foo/Dockstore.cwl", EntryVersionHelper.removeWorkingDirectory("foo/Dockstore.cwl", "Dockstore.cwl"));
        Assert.assertEquals("foo/Dockstore.cwl", EntryVersionHelper.removeWorkingDirectory("./foo/Dockstore.cwl", "Dockstore.cwl"));
        Assert.assertEquals("foo/Dockstore.cwl", EntryVersionHelper.removeWorkingDirectory("/foo/Dockstore.cwl", "Dockstore.cwl"));
        // Edge case of filename also being part of the path
        Assert.assertEquals("Dockstore.cwl/Dockstore.cwl", EntryVersionHelper.removeWorkingDirectory("/Dockstore.cwl/Dockstore.cwl", "Dockstore.cwl"));

        Assert.assertEquals("Dockstore.cwl", EntryVersionHelper.removeWorkingDirectory("./Dockstore.cwl", "Dockstore.cwl"));
        Assert.assertEquals("Dockstore.cwl", EntryVersionHelper.removeWorkingDirectory("././Dockstore.cwl", "Dockstore.cwl"));
        Assert.assertEquals(".dockstore.yml", EntryVersionHelper.removeWorkingDirectory(".dockstore.yml", ".dockstore.yml"));
        Assert.assertEquals(".dockstore.yml", EntryVersionHelper.removeWorkingDirectory("/.dockstore.yml", ".dockstore.yml"));
        Assert.assertEquals(".dockstore.yml", EntryVersionHelper.removeWorkingDirectory("./.dockstore.yml", ".dockstore.yml"));
        Assert.assertEquals(".dockstore.yml", EntryVersionHelper.removeWorkingDirectory("././.dockstore.yml", ".dockstore.yml"));
    }
}
