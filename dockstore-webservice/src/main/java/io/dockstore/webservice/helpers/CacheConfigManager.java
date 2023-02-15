package io.dockstore.webservice.helpers;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import java.nio.file.Path;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.authorization.InstallationIDAuthorizationProvider;
import org.kohsuke.github.extras.authorization.JWTTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CacheConfigManager {

    private static final Logger LOG = LoggerFactory.getLogger(CacheConfigManager.class);

    private static final CacheConfigManager CACHE_CONFIG_MANAGER = new CacheConfigManager();

    private static LoadingCache<Long, GitHub> githubClientAPICache;

    private String appId;
    private String privateKeyFile;

    public static CacheConfigManager getInstance() {
        return CACHE_CONFIG_MANAGER;
    }

    /**
     * @param installationId App installation ID (per user)
     * @return github api client
     */
    private GitHub getGitHubClientFromInstallationId(long installationId) throws Exception {
        JWTTokenProvider tokenProvider = new JWTTokenProvider(appId, Path.of(privateKeyFile));
        final InstallationIDAuthorizationProvider installationIDAuthorizationProvider = new InstallationIDAuthorizationProvider(installationId, tokenProvider);
        return GitHubSourceCodeRepo.getBuilder(Long.toString(installationId)).withAuthorizationProvider(installationIDAuthorizationProvider).build();
    }

    /**
     * Initialize the cache for GitHub api clients
     */
    public void initCache(String githubAppId, String gitHubPrivateKeyFile) {
        this.appId = githubAppId;
        this.privateKeyFile = gitHubPrivateKeyFile;
        final int maxSize = 100;
        //providers self-renew
        if (githubClientAPICache == null) {
            githubClientAPICache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .recordStats()
                .build(installationId -> {
                    LOG.info("Fetching organization provider " + installationId + " from cache.");
                    return getGitHubClientFromInstallationId(installationId);
                });
        }
    }

    /**
     * Load github client from the cache
     * @param installationId
     * @return github api client
     */
    public GitHub getGitHubClientFromCache(long installationId) {
        try {
            CacheStats cacheStats = githubClientAPICache.stats();
            LOG.info(cacheStats.toString());
            return githubClientAPICache.get(installationId);
        } catch (Exception ex) {
            LOG.error("Error retrieving token", ex);
        }
        return null;
    }
}
