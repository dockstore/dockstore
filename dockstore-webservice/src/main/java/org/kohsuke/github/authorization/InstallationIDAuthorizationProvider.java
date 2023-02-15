package org.kohsuke.github.authorization;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.kohsuke.github.BetaApi;
import org.kohsuke.github.GHAppInstallation;
import org.kohsuke.github.GHAppInstallationToken;
import org.kohsuke.github.GitHub;

/**
 * An AuthorizationProvider that performs automatic token refresh for an AppInstallation.
 * Extremely similar to {@link OrgAppInstallationAuthorizationProvider} (I don't actually know why they chose to depend on organization name rather than installation id)
 */
public class InstallationIDAuthorizationProvider extends GitHub.DependentAuthorizationProvider {

    public static final int MARGIN_OF_SAFETY = 5;
    private final long installationId;

    private String authorization;

    private Instant validUntil = Instant.MIN;

    /**
     * Provides an AuthorizationProvider that performs automatic token refresh, based on an previously authenticated
     * github client.
     *
     * @param installationId
     * @param authorizationProvider
     *            A authorization provider that returns a JWT token that can be used to refresh the App Installation
     *            token from GitHub.
     */
    @BetaApi
    public InstallationIDAuthorizationProvider(long installationId,
            AuthorizationProvider authorizationProvider) {
        super(authorizationProvider);
        this.installationId = installationId;
    }

    @Override
    public String getEncodedAuthorization() throws IOException {
        synchronized (this) {
            if (authorization == null || Instant.now().isAfter(this.validUntil)) {
                String token = refreshToken();
                authorization = String.format("token %s", token);
            }
            return authorization;
        }
    }

    private String refreshToken() throws IOException {
        GitHub gitHub = this.gitHub();
        GHAppInstallation appInstallation = gitHub.getApp().getInstallationById(installationId); // Installation Id
        GHAppInstallationToken ghAppInstallationToken = appInstallation.createToken().create();
        this.validUntil = ghAppInstallationToken.getExpiresAt().toInstant().minus(Duration.ofMinutes(MARGIN_OF_SAFETY));

        return Objects.requireNonNull(ghAppInstallationToken.getToken());
    }
}
