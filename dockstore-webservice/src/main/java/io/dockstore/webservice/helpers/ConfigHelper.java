package io.dockstore.webservice.helpers;

import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class for setting the config variables for git commit info
 */
public final class ConfigHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigHelper.class);

    private ConfigHelper() {
    }

    /**
     * Reads a properties file with git info and returns abbreviated commitId and build version.
     * If file cannot be read, will log error and set config to "git property not found".
     * @param fileName properties file with git information
     * @return git commitID and buildVersion
     */
    public static GitInfo readGitProperties(final String fileName) {
        final Properties properties = new Properties();
        LOGGER.debug("Retrieving git properties from file: " + fileName);
        try {
            properties.load(GitInfo.class.getClassLoader().getResourceAsStream(fileName));
            return new GitInfo(String.valueOf(properties.get("git.commit.id.abbrev")), String.valueOf(properties.get("git.build.version")));
        } catch (Exception e) {
            LOGGER.error("Could not load git.properties", e);
            String propertyNotFound = "git property not found";
            return new GitInfo(propertyNotFound, propertyNotFound);
        }
    }

    public static final class GitInfo {
        public final String commitId;
        public final String buildVersion;

        private GitInfo(String commitId, String buildVersion) {
            this.commitId = commitId;
            this.buildVersion = buildVersion;
        }
    }

}
