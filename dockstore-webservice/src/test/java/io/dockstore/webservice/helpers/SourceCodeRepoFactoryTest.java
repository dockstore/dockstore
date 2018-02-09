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
        // test format 1
        final Map<String, String> stringStringMap1 = SourceCodeRepoFactory.parseGitUrl("git@github.com:ga4gh/dockstore-ui.git");
        Assert.assertTrue("values not found",
                stringStringMap1.containsKey("Source") && stringStringMap1.containsKey("Username") && stringStringMap1
                        .containsKey("Repository"));
        // test format 2
        final Map<String, String> stringStringMap2 = SourceCodeRepoFactory
                .parseGitUrl("git://github.com/denis-yuen/dockstore-whalesay.git");
        Assert.assertTrue("values not found",
                stringStringMap2.containsKey("Source") && stringStringMap2.containsKey("Username") && stringStringMap2
                        .containsKey("Repository"));
        // test garbage
        final Map<String, String> stringStringMap3 = SourceCodeRepoFactory.parseGitUrl("mostly harmless");
        Assert.assertTrue("should be null", stringStringMap3 == null);
    }

}