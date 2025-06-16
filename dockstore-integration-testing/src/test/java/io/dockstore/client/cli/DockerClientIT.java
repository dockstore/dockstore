/*
 * Copyright 2025 OICR and UCSC
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

package io.dockstore.client.cli;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.exception.DockerClientException;
import com.github.dockerjava.api.model.SearchItem;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dropwizard.testing.DropwizardTestSupport;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag(ConfidentialTest.NAME)
class DockerClientIT {

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
    void testOldSchemaDockerImages() throws IOException, InterruptedException {
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();

        try (DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder().dockerHost(config.getDockerHost())
            .sslConfig(config.getSSLConfig()).build(); DockerClient dockerClient = DockerClientImpl.getInstance(config, httpClient)) {

            // this does not return enough image to diagnose Docker image version
            List<SearchItem> items = dockerClient.searchImagesCmd("Java").exec();

            dockerClient.pullImageCmd("weischenfeldt/pcawg_sv_merge")
                .withTag("1.0.2")
                .exec(new PullImageResultCallback())
                .awaitCompletion();

            DockerClientException exception = assertThrows(DockerClientException.class, () -> {
                // note this just dies and you need a Docker client on the localhost to assess whether an image is the older schema which is than ideal
                // see https://docs.docker.com/engine/deprecated/#pushing-and-pulling-with-image-manifest-v2-schema-1
                // see also https://github.com/dockstore/dockstore/issues/5878
                dockerClient.pullImageCmd("memcached")
                    .withTag("1.4.22")
                    .exec(new PullImageResultCallback())
                    .awaitCompletion();
            });
            assertTrue((exception.getMessage().contains("Docker Image Format v1 and Docker Image manifest version 2, schema 1 support is disabled")));
        }
    }
}
