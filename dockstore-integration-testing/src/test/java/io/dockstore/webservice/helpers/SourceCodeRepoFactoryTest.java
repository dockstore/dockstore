/*
 *    Copyright 2017 OICR
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package io.dockstore.webservice.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.NonConfidentialTest;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.core.BioWorkflow;
import io.dockstore.webservice.core.Tool;
import io.dropwizard.testing.DropwizardTestSupport;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * @author dyuen
 */
@Tag(NonConfidentialTest.NAME)
public class SourceCodeRepoFactoryTest {

    public static final DropwizardTestSupport<DockstoreWebserviceConfiguration> SUPPORT = new DropwizardTestSupport<>(
            DockstoreWebserviceApplication.class, CommonTestUtilities.PUBLIC_CONFIG_PATH);


    @BeforeEach
    public void setUp() throws Exception {
        CommonTestUtilities.dropAndRecreateNoTestData(SUPPORT, CommonTestUtilities.PUBLIC_CONFIG_PATH);
        SUPPORT.before();
    }

    @AfterAll
    public static void afterClass() {
        SUPPORT.getEnvironment().healthChecks().shutdown();
        SUPPORT.after();
    }

    @Test
    void parseGitUrl() {
        // https://stackoverflow.com/questions/59081778/rules-for-special-characters-in-github-repository-name
        // test format 1
        final Optional<Map<String, String>> stringStringMapOpt = SourceCodeRepoFactory
                .parseGitUrl("git@github.com:dockstore/dockstore-ui.git");
        assertNotNull(stringStringMapOpt);
        assertEquals("github.com", stringStringMapOpt.get().get(SourceCodeRepoFactory.GIT_URL_SOURCE_KEY));
        assertEquals("dockstore", stringStringMapOpt.get().get(SourceCodeRepoFactory.GIT_URL_USER_KEY));
        assertEquals("dockstore-ui", stringStringMapOpt.get().get(SourceCodeRepoFactory.GIT_URL_REPOSITORY_KEY));

        final Optional<Map<String, String>> stringStringMap1 = SourceCodeRepoFactory
                .parseGitUrl("git@github.com:dockstore/dockstore-ui.git", Optional.of("github.com"));
        assertNotNull(stringStringMap1);
        assertEquals("github.com", stringStringMap1.get().get(SourceCodeRepoFactory.GIT_URL_SOURCE_KEY));
        assertEquals("dockstore", stringStringMap1.get().get(SourceCodeRepoFactory.GIT_URL_USER_KEY));
        assertEquals("dockstore-ui", stringStringMap1.get().get(SourceCodeRepoFactory.GIT_URL_REPOSITORY_KEY));

        final Optional<Map<String, String>> repoMapWithPeriodAndHyphen = SourceCodeRepoFactory
                .parseGitUrl("git@github.com:DockstoreTestUser2/wdl-1.0-work_flow.git", Optional.of("github.com"));
        assertNotNull(repoMapWithPeriodAndHyphen);
        assertEquals("github.com", repoMapWithPeriodAndHyphen.get().get(SourceCodeRepoFactory.GIT_URL_SOURCE_KEY));
        assertEquals("DockstoreTestUser2", repoMapWithPeriodAndHyphen.get().get(SourceCodeRepoFactory.GIT_URL_USER_KEY));
        assertEquals("wdl-1.0-work_flow", repoMapWithPeriodAndHyphen.get().get(SourceCodeRepoFactory.GIT_URL_REPOSITORY_KEY));

        // test format 2
        final Optional<Map<String, String>> stringStringMap2 = SourceCodeRepoFactory
                .parseGitUrl("git://github.com/denis-yuen/dockstore-whalesay.git");
        assertNotNull(stringStringMap2);
        assertEquals("github.com", stringStringMap2.get().get(SourceCodeRepoFactory.GIT_URL_SOURCE_KEY));
        assertEquals("denis-yuen", stringStringMap2.get().get(SourceCodeRepoFactory.GIT_URL_USER_KEY));
        assertEquals("dockstore-whalesay", stringStringMap2.get().get(SourceCodeRepoFactory.GIT_URL_REPOSITORY_KEY));

        final Optional<Map<String, String>> repoMapWithPeriodAndHyphen2 = SourceCodeRepoFactory
                .parseGitUrl("git://github.com/DockstoreTestUser2/wdl-1.0-work_flow.git");
        assertNotNull(repoMapWithPeriodAndHyphen2);
        assertEquals("github.com", repoMapWithPeriodAndHyphen2.get().get(SourceCodeRepoFactory.GIT_URL_SOURCE_KEY));
        assertEquals("DockstoreTestUser2", repoMapWithPeriodAndHyphen2.get().get(SourceCodeRepoFactory.GIT_URL_USER_KEY));
        assertEquals("wdl-1.0-work_flow", repoMapWithPeriodAndHyphen2.get().get(SourceCodeRepoFactory.GIT_URL_REPOSITORY_KEY));

        // test garbage
        Optional<Map<String, String>> stringStringMap3 = SourceCodeRepoFactory.parseGitUrl("mostly harmless");
        assertTrue(stringStringMap3.isEmpty());

        stringStringMap3 = SourceCodeRepoFactory.parseGitUrl("mostly harmless", Optional.of("github.com"));
        assertTrue(stringStringMap3.isEmpty());

        final Optional<Map<String, String>> stringStringMapBadOpt = SourceCodeRepoFactory
                .parseGitUrl("git@github.com:dockstore/dockstore-ui.git", Optional.of("bad source"));
        assertTrue(stringStringMapBadOpt.isEmpty());

    }

    /**
     * Tests that parsing a GitLab URL works
     */
    @Test
    void testGitLabUrlRegexParsing() {
        GitLabSourceCodeRepo repo = new GitLabSourceCodeRepo("fakeUser", "fakeToken");
        final BioWorkflow entry = new BioWorkflow();

        /* Test good URLs */
        entry.setGitUrl("git@gitlab.com:dockstore/dockstore-ui.git");
        String gitlabId = repo.getRepositoryId(entry);
        assertEquals("dockstore/dockstore-ui", gitlabId, "gitlab ID parse check");

        entry.setGitUrl("git@gitlab.com:dockstore-cow/goat.git");
        gitlabId = repo.getRepositoryId(entry);
        assertEquals("dockstore-cow/goat", gitlabId, "gitlab ID parse check");

        entry.setGitUrl("git@gitlab.com:dockstore.dot/goat.bat.git");
        gitlabId = repo.getRepositoryId(entry);
        assertEquals("dockstore.dot/goat.bat", gitlabId, "gitlab ID parse check");

        entry.setGitUrl("git@gitlab.com:dockstore.dot/goat.git");
        gitlabId = repo.getRepositoryId(entry);
        assertEquals("dockstore.dot/goat", gitlabId, "gitlab ID parse check");

        /* Test bad URLs */
        entry.setGitUrl("git@gitlab.com/dockstore/dockstore-ui.git");
        gitlabId = repo.getRepositoryId(entry);
        assertNull(gitlabId, "gitlab ID parse check");

        entry.setGitUrl("git@gitlab.com:dockstore:dockstore-ui.git");
        gitlabId = repo.getRepositoryId(entry);
        assertNull(gitlabId, "gitlab ID parse check");

        entry.setGitUrl("git@gitlab.com:/dockstore-ui.git");
        gitlabId = repo.getRepositoryId(entry);
        assertNull(gitlabId, "gitlab ID parse check");

        entry.setGitUrl("git@gitlab.com:dockstore");
        gitlabId = repo.getRepositoryId(entry);
        assertNull(gitlabId, "gitlab ID parse check");

        entry.setGitUrl("git@gitlab.com:dockstore/dockstore-ui");
        gitlabId = repo.getRepositoryId(entry);
        assertNull(gitlabId, "gitlab ID parse check");

    }

    /**
     * Tests that parsing a GitHub URL works
     */
    @Test
    void testGitHubUrlRegexParsing() {
        GitHubSourceCodeRepo repo = new GitHubSourceCodeRepo("fakeUser", "fakeToken");
        final Tool entry = new Tool();

        /* Test good URLs */
        entry.setGitUrl("git@github.com:dockstore/dockstore-ui.git");
        String githubId = repo.getRepositoryId(entry);
        assertEquals("dockstore/dockstore-ui", githubId, "GitHub ID parse check");

        entry.setGitUrl("git@github.com:dockstore-cow/goat.git");
        githubId = repo.getRepositoryId(entry);
        assertEquals("dockstore-cow/goat", githubId, "GitHub ID parse check");

        entry.setGitUrl("git@github.com:dockstore.dot/goat.bat.git");
        githubId = repo.getRepositoryId(entry);
        assertEquals("dockstore.dot/goat.bat", githubId, "GitHub ID parse check");

        entry.setGitUrl("git@github.com:dockstore.dot/goat.git");
        githubId = repo.getRepositoryId(entry);
        assertEquals("dockstore.dot/goat", githubId, "GitHub ID parse check");

        /* Test bad URLs */
        entry.setGitUrl("git@github.com/dockstore/dockstore-ui.git");
        githubId = repo.getRepositoryId(entry);
        assertNull(githubId, "GitHub ID parse check");

        entry.setGitUrl("git@github.com:dockstore:dockstore-ui.git");
        githubId = repo.getRepositoryId(entry);
        assertNull(githubId, "GitHub ID parse check");

        entry.setGitUrl("git@github.com:/dockstore-ui.git");
        githubId = repo.getRepositoryId(entry);
        assertNull(githubId, "GitHub ID parse check");

        entry.setGitUrl("git@github.com:dockstore");
        githubId = repo.getRepositoryId(entry);
        assertNull(githubId, "GitHub ID parse check");

        entry.setGitUrl("git@github.com:dockstore/dockstore-ui");
        githubId = repo.getRepositoryId(entry);
        assertNull(githubId, "GitHub ID parse check");

    }

}
