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
package io.dockstore.client.cli;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.SortedMap;

import com.codahale.metrics.Gauge;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.ConnectionLeakUtil;
import io.dockstore.common.Constants;
import io.dockstore.common.Utilities;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dropwizard.testing.DropwizardTestSupport;
import io.swagger.client.ApiClient;
import io.swagger.client.auth.ApiKeyAuth;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.dbutils.handlers.ScalarHandler;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.experimental.categories.Category;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import static io.dockstore.common.CommonTestUtilities.getTestingPostgres;

/**
 * Base integration test class
 * A default configuration that cleans the database between tests
 */
@Category(ConfidentialTest.class)
public class BaseIT {

    public static final String ADMIN_USERNAME = "admin@admin.com";
    public static final String USER_1_USERNAME = "DockstoreTestUser";
    public static final String USER_2_USERNAME = "DockstoreTestUser2";
    static final String OTHER_USERNAME = "OtherUser";
    final String CURATOR_USERNAME = "curator@curator.com";

    public static final DropwizardTestSupport<DockstoreWebserviceConfiguration> SUPPORT = new DropwizardTestSupport<>(
        DockstoreWebserviceApplication.class, CommonTestUtilities.CONFIDENTIAL_CONFIG_PATH);
    public static ConnectionLeakUtil connectionLeakUtil;
    public static final boolean enableConnectionLeakDetection = true;
    @BeforeClass
    public static void dropAndRecreateDB() throws Exception {
        CommonTestUtilities.dropAndRecreateNoTestData(SUPPORT);
        SUPPORT.before();
        initConnectionLeakUtility();
    }

    public static void initConnectionLeakUtility() {
        String url = SUPPORT.getConfiguration().getDataSourceFactory().getUrl();
        String user = SUPPORT.getConfiguration().getDataSourceFactory().getUser();
        String password = SUPPORT.getConfiguration().getDataSourceFactory().getPassword();
        if ( enableConnectionLeakDetection ) {
            connectionLeakUtil = new ConnectionLeakUtil(url, user, password);
        }
    }

    public static void assertNoLeaks() throws Exception {
        if ( enableConnectionLeakDetection ) {
            connectionLeakUtil.assertNoLeaks();
        }
    }


    public static void assertNoMetricsLeaks(DropwizardTestSupport<DockstoreWebserviceConfiguration> support) {
        SortedMap<String, Gauge> gauges = support.getEnvironment().metrics().getGauges();
        int active = (int)gauges.get("io.dropwizard.db.ManagedPooledDataSource.hibernate.active").getValue();
        int waiting = (int)gauges.get("io.dropwizard.db.ManagedPooledDataSource.hibernate.waiting").getValue();
        Assert.assertEquals(0, active);
        Assert.assertEquals(0, waiting);
    }

    @AfterClass
    public static void afterClass() {
        SUPPORT.after();
    }

    @After
    public void after() throws Exception {
        assertNoLeaks();
        assertNoMetricsLeaks(SUPPORT);
    }

    @Before
    public void resetDBBetweenTests() throws Exception {
        CommonTestUtilities.dropAndCreateWithTestData(SUPPORT, false);
    }

    @Rule
    public TestRule watcher = new TestWatcher() {
        protected void starting(Description description) {
            System.out.println("Starting test: " + description.getMethodName());
        }
    };

    /**
     * Shared convenience method
     * @return
     */
    protected static ApiClient getWebClient(boolean authenticated, String username) {
        final CommonTestUtilities.TestingPostgres testingPostgres = getTestingPostgres();
        File configFile = FileUtils.getFile("src", "test", "resources", "config2");
        INIConfiguration parseConfig = Utilities.parseConfig(configFile.getAbsolutePath());
        ApiClient client = new ApiClient();
        client.setBasePath(parseConfig.getString(Constants.WEBSERVICE_BASE_PATH));
        if (authenticated) {
            client.addDefaultHeader("Authorization", "Bearer " + (testingPostgres
                    .runSelectStatement("select content from token where tokensource='dockstore' and username= '" + username + "';",
                            new ScalarHandler<>())));
        }
        return client;
    }

    /**
     * the following were migrated from SwaggerClientIT and can be eventually merged. Note different config file used
     */

    protected static ApiClient getWebClient(String username) {
        return getWebClient(true, username);
    }

    protected static ApiClient getWebClient() {
        return getWebClient(true, false);
    }

    static ApiClient getAdminWebClient() {
        return getWebClient(true, true);
    }

    protected static ApiClient getAnonymousWebClient() {
        File configFile = FileUtils.getFile("src", "test", "resources", "config");
        INIConfiguration parseConfig = Utilities.parseConfig(configFile.getAbsolutePath());
        ApiClient client = new ApiClient();
        client.setBasePath(parseConfig.getString(Constants.WEBSERVICE_BASE_PATH));
        return client;
    }

    protected static ApiClient getWebClient(boolean correctUser, boolean admin) {
        File configFile = FileUtils.getFile("src", "test", "resources", "config");
        INIConfiguration parseConfig = Utilities.parseConfig(configFile.getAbsolutePath());
        ApiClient client = new ApiClient();
        ApiKeyAuth bearer = (ApiKeyAuth) client.getAuthentication("BEARER");
        bearer.setApiKeyPrefix("BEARER");
        bearer.setApiKey((correctUser ?
                parseConfig.getString(admin ? Constants.WEBSERVICE_TOKEN_USER_1 : Constants.WEBSERVICE_TOKEN_USER_2) :
                "foobar"));
        client.setBasePath(parseConfig.getString(Constants.WEBSERVICE_BASE_PATH));
        return client;
    }
}
