/*
 *    Copyright 2018 OICR
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

import io.dockstore.common.*;
import io.openapi.api.ApiException;

import io.swagger.client.ApiClient;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import javax.ws.rs.core.GenericType;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * This test suite tests various garbage inputs and confirms its 500 error.
 */
@Category({ ConfidentialTest.class })
public class GarbageInputsIT extends BaseIT {

    @Test
    public void testPublishedToolsGarbageInput() throws ApiException, IOException {
        // Set up webservice
        ApiClient webClient = getWebClient(USER_2_USERNAME, testingPostgres);

        byte[] arbitraryURL = CommonTestUtilities.getArbitraryURL(
                "/containers/published?offset=-1%20OR%203%2B783-783-1=0%2B0%2B0%2B1%20--%20&limit=100&filter=&sortCol=stars&sortOrder=desc", new GenericType<>() {
                }, webClient);
        File tempZip = File.createTempFile("temp", "zip");
        Path write = Files.write(tempZip.toPath(), arbitraryURL);
    }


}
