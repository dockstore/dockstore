/*
 * Copyright 2021 OICR and UCSC
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.dockstore.webservice.helpers;

import com.google.api.client.util.Charsets;
import com.google.common.io.Files;
import io.dockstore.webservice.helpers.CheckUrlHelper.TestFileType;
import io.dropwizard.testing.ResourceHelpers;
import java.io.File;
import java.io.IOException;
import java.util.Set;
import org.junit.Assert;
import org.junit.Test;

public class CheckUrlHelperTest {

    @Test
    public void getUrlsFromJSON() throws IOException {
        File file = new File(ResourceHelpers.resourceFilePath("testParameterFile1.json"));
        String s = Files.asCharSource(file, Charsets.UTF_8).read();
        Set<String> urls = CheckUrlHelper.getUrlsFromJSON(s);
        Assert.assertEquals("Should have 69 minus 4 from JSON spacing minus 4 from quay.io, boolean, number, duplicate",
            61, urls.size());
    }

    @Test
    public void getUrlsFromYAML() throws IOException {
        File file = new File(ResourceHelpers.resourceFilePath("testParameterFile1.yaml"));
        String s = Files.asCharSource(file, Charsets.UTF_8).read();
        Set<String> urls = CheckUrlHelper.getUrlsFromYAML(s);
        Assert.assertEquals("Should have same amount of URLs as the JSON equivalent above",
            61, urls.size());
    }

    @Test
    public void getUrlsFromInvalidFile() throws IOException {
        File file = new File(ResourceHelpers.resourceFilePath("valid_description_example.wdl"));
        String s = Files.asCharSource(file, Charsets.UTF_8).read();
        Assert.assertTrue(CheckUrlHelper.checkTestParameterFile(s, "fakeBaseUrl", TestFileType.YAML).isEmpty());
        Assert.assertTrue(CheckUrlHelper.checkTestParameterFile(s, "fakeBaseUrl", TestFileType.JSON).isEmpty());
    }
}
