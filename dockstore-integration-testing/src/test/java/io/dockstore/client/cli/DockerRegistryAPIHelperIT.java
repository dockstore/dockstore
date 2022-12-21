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

package io.dockstore.client.cli;

import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.NonConfidentialTest;
import io.dockstore.common.Registry;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.helpers.DockerRegistryAPIHelper;
import io.dropwizard.testing.DropwizardTestSupport;
import java.util.Optional;
import okhttp3.Response;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Category(NonConfidentialTest.class)
public class DockerRegistryAPIHelperIT {

    public static final DropwizardTestSupport<DockstoreWebserviceConfiguration> SUPPORT = new DropwizardTestSupport<>(
            DockstoreWebserviceApplication.class, CommonTestUtilities.PUBLIC_CONFIG_PATH);


    @Before
    public void setUp() throws Exception {
        CommonTestUtilities.dropAndRecreateNoTestData(SUPPORT, CommonTestUtilities.PUBLIC_CONFIG_PATH);
        SUPPORT.before();
    }

    @AfterClass
    public static void afterClass() {
        SUPPORT.getEnvironment().healthChecks().shutdown();
        SUPPORT.after();
    }

    /**
     * Test that the calculated digest matches the actual digest of the image.
     */
    @Test
    public void testCalculateDockerImageDigest() {
        // GHCR image used: ghcr.io/helm/tiller@sha256:4c43eb385032945cad047d2350e4945d913b90b3ab43ee61cecb32a495c6df0f (associated tag is 'v2.17.0')
        String repo = "helm/tiller";
        String digest = "sha256:4c43eb385032945cad047d2350e4945d913b90b3ab43ee61cecb32a495c6df0f";
        Optional<String> token = DockerRegistryAPIHelper.getDockerToken(Registry.GITHUB_CONTAINER_REGISTRY.getDockerPath(), repo);
        Assert.assertTrue(token.isPresent());
        Optional<Response> manifestResponse = DockerRegistryAPIHelper.getDockerManifest(token.get(), Registry.GITHUB_CONTAINER_REGISTRY.getDockerPath(), repo, digest);
        Assert.assertTrue(manifestResponse.get().isSuccessful());
        String calculatedDigest = "sha256:" + DockerRegistryAPIHelper.calculateDockerImageDigest(manifestResponse.get());
        Assert.assertEquals(digest, calculatedDigest);

        // Amazon ECR image used: public.ecr.aws/nginx/unit:1.24.0-minimal
        repo = "nginx/unit";
        digest = "sha256:5711186c4c24cf544c1d6ea1f64de288fc3d1f47bc506cae251a75047b15a89a";
        token = DockerRegistryAPIHelper.getDockerToken(Registry.AMAZON_ECR.getDockerPath(), repo);
        Assert.assertTrue(token.isPresent());
        manifestResponse = DockerRegistryAPIHelper.getDockerManifest(token.get(), Registry.AMAZON_ECR.getDockerPath(), repo, digest);
        Assert.assertTrue(manifestResponse.get().isSuccessful());
        calculatedDigest = "sha256:" + DockerRegistryAPIHelper.calculateDockerImageDigest(manifestResponse.get());
        Assert.assertEquals(digest, calculatedDigest);
    }
}
