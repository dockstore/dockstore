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
        final Map<String, String> stringStringMap1 = SourceCodeRepoFactory.parseGitUrl("git@github.com:dockstore/dockstore-ui.git");
        Assert.assertNotNull(stringStringMap1);
        Assert.assertTrue("values not found",
                stringStringMap1.containsKey("Source") && stringStringMap1.get("Source").equals("github.com")
                        && stringStringMap1.containsKey("Username") && stringStringMap1.get("UserName").equals("dockstore")
                        && stringStringMap1.containsKey("Repository") && stringStringMap1.get("Repository").equals("dockstore-ui"));

        final Map<String, String> repoMapWithPeriodAndHyphen = SourceCodeRepoFactory.parseGitUrl("git@github.com:DockstoreTestUser2/wdl-1.0-work_flow.git");
        Assert.assertNotNull(repoMapWithPeriodAndHyphen);
        Assert.assertTrue("values not found",
                repoMapWithPeriodAndHyphen.containsKey("Source") && repoMapWithPeriodAndHyphen.get("Source").equals("github.com")
                        && repoMapWithPeriodAndHyphen.containsKey("Username") && repoMapWithPeriodAndHyphen.get("UserName").equals("DockstoreTestUser2")
                        && repoMapWithPeriodAndHyphen.containsKey("Repository") && repoMapWithPeriodAndHyphen.get("Repository").equals("wdl-1.0-work_flow"));

        // test format 2
        final Map<String, String> stringStringMap2 = SourceCodeRepoFactory
                .parseGitUrl("git://github.com/denis-yuen/dockstore-whalesay.git");
        Assert.assertNotNull(stringStringMap2);
        Assert.assertTrue("values not found",
                stringStringMap2.containsKey("Source") && stringStringMap2.get("Source").equals("github.com")
                        && stringStringMap2.containsKey("Username") && stringStringMap2.get("UserName").equals("denis-yuen")
                        && stringStringMap2.containsKey("Repository") && stringStringMap2.get("Repository").equals("dockstore-whalesay"));

        final Map<String, String> repoMapWithPeriodAndHyphen2 = SourceCodeRepoFactory.parseGitUrl("git://github.com/DockstoreTestUser2/wdl-1.0-work_flow.git");
        Assert.assertNotNull(repoMapWithPeriodAndHyphen2);
        Assert.assertTrue("values not found",
                repoMapWithPeriodAndHyphen2.containsKey("Source") && repoMapWithPeriodAndHyphen2.get("Source").equals("github.com")
                        && repoMapWithPeriodAndHyphen2.containsKey("Username") && repoMapWithPeriodAndHyphen2.get("UserName").equals("DockstoreTestUser2")
                        && repoMapWithPeriodAndHyphen2.containsKey("Repository") && repoMapWithPeriodAndHyphen2.get("Repository").equals("wdl-1.0-work_flow"));

        // test garbage
        final Map<String, String> stringStringMap3 = SourceCodeRepoFactory.parseGitUrl("mostly harmless");
        Assert.assertNull("should be null", stringStringMap3);
    }

}
