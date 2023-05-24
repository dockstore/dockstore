/*
 * Copyright 2023 OICR and UCSC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
