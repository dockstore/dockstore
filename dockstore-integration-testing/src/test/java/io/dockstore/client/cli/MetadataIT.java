/*
 *    Copyright 2019 OICR
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
package io.dockstore.client.cli;

import io.swagger.client.ApiClient;
import io.swagger.client.api.MetadataApi;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.rules.ExpectedException;

public class MetadataIT extends BaseIT {
    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();

    @Rule
    public ExpectedException thrown= ExpectedException.none();




    @Test
    public void testRunnerDependencies() {
        ApiClient apiClient = getWebClient();
        MetadataApi metadataApi = new MetadataApi(apiClient);
        String runnerDependencies = metadataApi.getRunnerDependencies("1.5.4", "2", "cwltool", "text");
        Assert.assertTrue(runnerDependencies.contains("cwltool==1.0.20180403145700"));
        runnerDependencies = metadataApi.getRunnerDependencies("1.6.0", "2", "cwltool", "text");
        Assert.assertTrue(runnerDependencies.contains("cwltool==1.0.20181217162649"));
        runnerDependencies = metadataApi.getRunnerDependencies("1.6.4", "2", "cwltool", "text");
        Assert.assertTrue(runnerDependencies.contains("cwltool==1.0.20181217162649"));
    }


}
