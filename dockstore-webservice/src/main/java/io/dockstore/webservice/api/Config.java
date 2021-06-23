package io.dockstore.webservice.api;

import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.helpers.ConfigHelper;
import io.swagger.annotations.ApiModel;
import java.lang.reflect.InvocationTargetException;
import org.apache.commons.beanutils.BeanUtils;

@ApiModel(description = "Configuration information for UI clients of the Dockstore webservice.")
public final class Config extends DockstoreWebserviceConfiguration.UIConfig {

    /**
     * Properties that aren't in UIConfig
     */

    private String githubClientId;
    private String quayIoClientId;
    private String bitBucketClientId;
    private String gitlabClientId;
    private String zenodoClientId;
    private String googleClientId;
    private String orcidClientId;
    private String discourseUrl;
    private String gitCommitId;
    private String gitBuildVersion;


    private Config() {
    }

    public static Config fromWebConfig(DockstoreWebserviceConfiguration webConfig)
            throws InvocationTargetException, IllegalAccessException {
        final Config config = new Config();
        config.githubClientId = webConfig.getGithubClientID();
        config.quayIoClientId = webConfig.getQuayClientID();
        config.bitBucketClientId = webConfig.getBitbucketClientID();
        config.gitlabClientId = webConfig.getGitlabClientID();
        config.zenodoClientId = webConfig.getZenodoClientID();
        config.googleClientId = webConfig.getGoogleClientID();
        config.orcidClientId = webConfig.getOrcidClientID();
        config.discourseUrl = webConfig.getDiscourseUrl();
        BeanUtils.copyProperties(config, webConfig.getUiConfig());
        final ConfigHelper.GitInfo gitInfo = ConfigHelper.readGitProperties("git.properties");
        config.gitCommitId = gitInfo.commitId;
        config.gitBuildVersion = gitInfo.buildVersion;
        return config;
    }

    public String getGithubClientId() {
        return githubClientId;
    }

    public String getQuayIoClientId() {
        return quayIoClientId;
    }

    public String getBitBucketClientId() {
        return bitBucketClientId;
    }

    public String getGitlabClientId() {
        return gitlabClientId;
    }

    public String getZenodoClientId() {
        return zenodoClientId;
    }

    public String getGoogleClientId() {
        return googleClientId;
    }

    public String getOrcidClientId() {
        return orcidClientId;
    }

    public String getDiscourseUrl() {
        return discourseUrl;
    }

    public String getGitCommitId() {
        return gitCommitId;
    }

    public String getGitBuildVersion() {
        return gitBuildVersion;
    }
}
