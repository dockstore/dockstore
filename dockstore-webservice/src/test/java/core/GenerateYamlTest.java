/*
 *    Copyright 2016 OICR
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

package core;

import java.io.File;
import java.io.IOException;
import java.net.URL;

import org.apache.commons.io.FileUtils;
import org.junit.ClassRule;
import org.junit.Test;

import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dropwizard.testing.ResourceHelpers;
import io.dropwizard.testing.junit.DropwizardAppRule;

/**
 * Not really a test, this is used to generate a yaml representation of our webservice for future reference.
 * @author dyuen
 */
public class GenerateYamlTest {

    @ClassRule
    public static final DropwizardAppRule<DockstoreWebserviceConfiguration> RULE = new DropwizardAppRule<>(
            DockstoreWebserviceApplication.class, ResourceHelpers.resourceFilePath("dockstore.yml"));


    @Test
    public void generateYAML() throws IOException {
        final int localPort = RULE.getLocalPort();
        final String swagger_filename = "/swagger.yaml";
        File destination = new File(System.getProperty("baseDir")+"/src/main/resources/", "swagger.yaml");
        final URL url = new URL("http", "localhost", localPort, swagger_filename);
        System.out.println(url.toString());
        FileUtils.copyURLToFile(url,destination);
    }
}
