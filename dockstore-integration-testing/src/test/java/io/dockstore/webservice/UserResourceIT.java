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

package io.dockstore.webservice;

import io.dockstore.client.cli.BaseIT;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dropwizard.testing.DropwizardTestSupport;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.api.UsersApi;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;

/**
 * Tests operations frrom the UserResource
 *
 * @author dyuen
 */
@Category(ConfidentialTest.class)
public class UserResourceIT extends BaseIT {

    public static final DropwizardTestSupport<DockstoreWebserviceConfiguration> SUPPORT = new DropwizardTestSupport<>(
        DockstoreWebserviceApplication.class, CommonTestUtilities.CONFIG_PATH);

    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();

    @Rule
    public ExpectedException thrown = ExpectedException.none();



    @Test
    public void testSelfDestruct() throws ApiException {
        ApiClient client = getAnonymousWebClient();
        UsersApi userApi = new UsersApi(client);
        // anon should not exist
        boolean shouldFail = false;
        try {
            userApi.getUser();
        } catch (ApiException e) {
            shouldFail = true;
        }
        Assert.assertTrue(shouldFail);

        client = getAdminWebClient();
        userApi = new UsersApi(client);
        Assert.assertTrue(userApi.getUser() != null);
        // try to delete with published workflows

        // add a few workflows
        // then unpublish them

        Assert.assertTrue(userApi.selfDestruct());
        // need to test that profiles are cascaded to and cleared
    }
}
