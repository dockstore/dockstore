package io.dockstore.common;

import com.google.common.io.Files;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;

import static io.dockstore.common.CommonTestUtilities.DUMMY_TOKEN_1;

/**
 * Created by jpatricia on 12/05/16.
 */
public class TestUtility {
    public static String getConfigFileLocation(boolean correctUser) throws IOException {
        return getConfigFileLocation(correctUser, true);
    }

    public static String getConfigFileLocation(boolean correctUser, boolean validPort) throws IOException {
        File tempDir = Files.createTempDir();
        final File tempFile = File.createTempFile("config", "config", tempDir);
        FileUtils.write(tempFile, "token: " + (correctUser ? DUMMY_TOKEN_1 : "foobar") + "\n");
        FileUtils.write(tempFile, "server-url: http://localhost:" + (validPort ? "8000" : "9001") + "\n", true);

        return tempFile.getAbsolutePath();
    }
}
