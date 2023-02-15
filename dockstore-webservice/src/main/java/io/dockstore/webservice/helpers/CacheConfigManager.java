package io.dockstore.webservice.helpers;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import java.util.concurrent.TimeUnit;
import org.kohsuke.github.GHAppInstallation;
import org.kohsuke.github.GHAppInstallationToken;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CacheConfigManager {

    private static final Logger LOG = LoggerFactory.getLogger(CacheConfigManager.class);

    private static final CacheConfigManager CACHE_CONFIG_MANAGER = new CacheConfigManager();

    private static LoadingCache<String, String> installationAccessTokenCache;

    private static volatile String jsonWebToken;

    public static CacheConfigManager getInstance() {
        return CACHE_CONFIG_MANAGER;
    }

    /**
     * Set the JWT
     * @param token JWT string
     */
    public static void setJsonWebToken(String token) {
        jsonWebToken = token;
    }

    /**
     * Given a JWT for the GitHub app and an installation ID, retrieve the Installation Access Token
     * @param installationId App installation ID (per user)
     * @return Installation Access Token
     */
    private String getInstallationAccessTokenFromInstallationId(String installationId) throws Exception {
        GitHub gitHubApp = new GitHubBuilder().withJwtToken(jsonWebToken).build();
        GHAppInstallation appInstallation = gitHubApp.getApp().getInstallationById(Long.parseLong(installationId)); // Installation Id
        GHAppInstallationToken appInstallationToken = appInstallation.createToken().create();
        return appInstallationToken.getToken();
    }

    /**
     * Initialize the cache for installation access tokens
     */
    public void initCache() {
        final int maxSize = 100;
        // GitHub token has a 1 hour expiration; leave a generous gap
        // so we don't fetch a token that may expire as we use it
        final int timeOutInMinutes = 58;
        if (installationAccessTokenCache == null) {
            installationAccessTokenCache = Caffeine.newBuilder()
                    .maximumSize(maxSize)
                    .expireAfterWrite(timeOutInMinutes, TimeUnit.MINUTES)
                    .recordStats()
                    .build(installationId -> {
                        LOG.info("Fetching installation " + installationId + " from cache.");
                        return getInstallationAccessTokenFromInstallationId(installationId);
                    });
        }
    }

    /**
     * Load installation access token from the cache
     * @param installationId App installation ID (per repository)
     * @return installation access token
     */
    public String getInstallationAccessTokenFromCache(String installationId) {
        try {
            CacheStats cacheStats = installationAccessTokenCache.stats();
            LOG.info(cacheStats.toString());
            return installationAccessTokenCache.get(installationId);
        } catch (Exception ex) {
            LOG.error("Error retrieving token", ex);
        }
        return null;
    }
}
