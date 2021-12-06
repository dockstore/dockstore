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

import java.util.Map;
import java.util.Optional;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author dyuen
 */
public class SourceCodeRepoFactoryTest {

    @Test
    public void parseGitUrl() {
        // https://stackoverflow.com/questions/59081778/rules-for-special-characters-in-github-repository-name
        // test format 1
        final Optional<Map<String, String>> stringStringMapOpt = SourceCodeRepoFactory
                .parseGitUrl("git@github.com:dockstore/dockstore-ui.git");
        Assert.assertNotNull(stringStringMapOpt);
        Assert.assertEquals("github.com", stringStringMapOpt.get().get(SourceCodeRepoFactory.GIT_URL_SOURCE_KEY));
        Assert.assertEquals("dockstore", stringStringMapOpt.get().get(SourceCodeRepoFactory.GIT_URL_USER_KEY));
        Assert.assertEquals("dockstore-ui", stringStringMapOpt.get().get(SourceCodeRepoFactory.GIT_URL_REPOSITORY_KEY));

        final Optional<Map<String, String>> stringStringMap1 = SourceCodeRepoFactory
                .parseGitUrl("git@github.com:dockstore/dockstore-ui.git", Optional.of("github.com"));
        Assert.assertNotNull(stringStringMap1);
        Assert.assertEquals("github.com", stringStringMap1.get().get(SourceCodeRepoFactory.GIT_URL_SOURCE_KEY));
        Assert.assertEquals("dockstore", stringStringMap1.get().get(SourceCodeRepoFactory.GIT_URL_USER_KEY));
        Assert.assertEquals("dockstore-ui", stringStringMap1.get().get(SourceCodeRepoFactory.GIT_URL_REPOSITORY_KEY));

        final Optional<Map<String, String>> repoMapWithPeriodAndHyphen = SourceCodeRepoFactory
                .parseGitUrl("git@github.com:DockstoreTestUser2/wdl-1.0-work_flow.git", Optional.of("github.com"));
        Assert.assertNotNull(repoMapWithPeriodAndHyphen);
        Assert.assertEquals("github.com", repoMapWithPeriodAndHyphen.get().get(SourceCodeRepoFactory.GIT_URL_SOURCE_KEY));
        Assert.assertEquals("DockstoreTestUser2", repoMapWithPeriodAndHyphen.get().get(SourceCodeRepoFactory.GIT_URL_USER_KEY));
        Assert.assertEquals("wdl-1.0-work_flow", repoMapWithPeriodAndHyphen.get().get(SourceCodeRepoFactory.GIT_URL_REPOSITORY_KEY));

        // test format 2
        final Optional<Map<String, String>> stringStringMap2 = SourceCodeRepoFactory
                .parseGitUrl("git://github.com/denis-yuen/dockstore-whalesay.git");
        Assert.assertNotNull(stringStringMap2);
        Assert.assertEquals("github.com", stringStringMap2.get().get(SourceCodeRepoFactory.GIT_URL_SOURCE_KEY));
        Assert.assertEquals("denis-yuen", stringStringMap2.get().get(SourceCodeRepoFactory.GIT_URL_USER_KEY));
        Assert.assertEquals("dockstore-whalesay", stringStringMap2.get().get(SourceCodeRepoFactory.GIT_URL_REPOSITORY_KEY));

        final Optional<Map<String, String>> repoMapWithPeriodAndHyphen2 = SourceCodeRepoFactory
                .parseGitUrl("git://github.com/DockstoreTestUser2/wdl-1.0-work_flow.git");
        Assert.assertNotNull(repoMapWithPeriodAndHyphen2);
        Assert.assertEquals("github.com", repoMapWithPeriodAndHyphen2.get().get(SourceCodeRepoFactory.GIT_URL_SOURCE_KEY));
        Assert.assertEquals("DockstoreTestUser2", repoMapWithPeriodAndHyphen2.get().get(SourceCodeRepoFactory.GIT_URL_USER_KEY));
        Assert.assertEquals("wdl-1.0-work_flow", repoMapWithPeriodAndHyphen2.get().get(SourceCodeRepoFactory.GIT_URL_REPOSITORY_KEY));

        // test garbage
        Optional<Map<String, String>> stringStringMap3 = SourceCodeRepoFactory.parseGitUrl("mostly harmless");
        Assert.assertTrue(stringStringMap3.isEmpty());

        stringStringMap3 = SourceCodeRepoFactory.parseGitUrl("mostly harmless", Optional.of("github.com"));
        Assert.assertTrue(stringStringMap3.isEmpty());

        final Optional<Map<String, String>> stringStringMapBadOpt = SourceCodeRepoFactory
                .parseGitUrl("git@github.com:dockstore/dockstore-ui.git", Optional.of("bad source"));
        Assert.assertTrue(stringStringMapBadOpt.isEmpty());

    }

}
