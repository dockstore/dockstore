package io.github.collaboratory;

import com.amazonaws.AmazonClientException;
import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.File;

/**
 * @author dyuen
 */
public class LauncherTest {

        @Rule
        public ExpectedException expectedEx = ExpectedException.none();

        // @Test
        // public void testMain() throws Exception {
        //         File iniFile = FileUtils.getFile("src", "test", "resources", "launcher.ini");
        //         File jsonFile = FileUtils.getFile("src", "test", "resources", "collab.json");
        //         expectedEx.expect(AmazonClientException.class);
        //         expectedEx.expectMessage("Unable to load AWS credentials from any provider in the chain");
        //         new Launcher(new String[] { "--config", iniFile.getAbsolutePath(), "--descriptor", jsonFile.getAbsolutePath() });
        // }

        @Test
        public void testCWL() throws Exception {
                File iniFile = FileUtils.getFile("src", "test", "resources", "launcher.ini");
                File cwlFile = FileUtils.getFile("src", "test", "resources", "collab.cwl");
                File jobFile = FileUtils.getFile("src", "test", "resources", "collab-cwl-job.json");
                new LauncherCWL(new String[] { "--config", iniFile.getAbsolutePath(),
                                               "--descriptor", cwlFile.getAbsolutePath(),
                                               "--job", jobFile.getAbsolutePath()});
        }
}
