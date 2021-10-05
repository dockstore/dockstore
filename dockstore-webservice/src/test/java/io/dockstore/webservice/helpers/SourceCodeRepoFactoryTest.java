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
        final Map<String, String> stringStringMap1 = SourceCodeRepoFactory
                .parseGitUrl("git@github.com:dockstore/dockstore-ui.git");
        Assert.assertNotNull(stringStringMap1);
        Assert.assertEquals("github.com", stringStringMap1.get("Source"));
        Assert.assertEquals("dockstore", stringStringMap1.get("Username"));
        Assert.assertEquals("dockstore-ui", stringStringMap1.get("Repository"));

        final Map<String, String> repoMapWithPeriodAndHyphen = SourceCodeRepoFactory
                .parseGitUrl("git@github.com:DockstoreTestUser2/wdl-1.0-work_flow.git");
        Assert.assertNotNull(repoMapWithPeriodAndHyphen);
        Assert.assertEquals("github.com", repoMapWithPeriodAndHyphen.get("Source"));
        Assert.assertEquals("DockstoreTestUser2", repoMapWithPeriodAndHyphen.get("Username"));
        Assert.assertEquals("wdl-1.0-work_flow", repoMapWithPeriodAndHyphen.get("Repository"));

        // test format 2
        final Map<String, String> stringStringMap2 = SourceCodeRepoFactory
                .parseGitUrl("git://github.com/denis-yuen/dockstore-whalesay.git");
        Assert.assertNotNull(stringStringMap2);
        Assert.assertEquals("github.com", stringStringMap2.get("Source"));
        Assert.assertEquals("denis-yuen", stringStringMap2.get("Username"));
        Assert.assertEquals("dockstore-whalesay", stringStringMap2.get("Repository"));

        final Map<String, String> repoMapWithPeriodAndHyphen2 = SourceCodeRepoFactory
                .parseGitUrl("git://github.com/DockstoreTestUser2/wdl-1.0-work_flow.git");
        Assert.assertNotNull(repoMapWithPeriodAndHyphen2);
        Assert.assertEquals("github.com", repoMapWithPeriodAndHyphen2.get("Source"));
        Assert.assertEquals("DockstoreTestUser2", repoMapWithPeriodAndHyphen2.get("Username"));
        Assert.assertEquals("wdl-1.0-work_flow", repoMapWithPeriodAndHyphen2.get("Repository"));

        // test garbage
        final Map<String, String> stringStringMap3 = SourceCodeRepoFactory.parseGitUrl("mostly harmless");
        Assert.assertNull("should be null", stringStringMap3);
    }

}
