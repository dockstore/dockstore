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

package io.dockstore.client.cli;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dropwizard.testing.ResourceHelpers;
import io.dropwizard.testing.junit.DropwizardAppRule;
import io.swagger.client.ApiException;
import io.swagger.client.api.UsersApi;
import io.swagger.client.model.SourceFile;
import io.swagger.client.model.User;
import io.swagger.quay.client.api.UserApi;

import static io.dockstore.common.CommonTestUtilities.clearState;
import static org.mockito.Mockito.mock;
import static org.powermock.api.easymock.PowerMock.replayAll;
import static org.powermock.api.easymock.PowerMock.verifyAll;
import static org.powermock.api.mockito.PowerMockito.doReturn;
import static org.powermock.api.mockito.PowerMockito.spy;
import static org.powermock.api.mockito.PowerMockito.when;
import static org.powermock.api.mockito.PowerMockito.whenNew;

/**
 * These tests use mocking to simulate responses from GitHub, BitBucket, and Quay.io
 * 
 * @author dyuen
 */
@PowerMockIgnore("javax.management.*")
@RunWith(PowerMockRunner.class)
@PrepareForTest({Client.class, UserApi.class})
public class MockedIT {

    @ClassRule
    public static final DropwizardAppRule<DockstoreWebserviceConfiguration> RULE = new DropwizardAppRule<>(
            DockstoreWebserviceApplication.class, ResourceHelpers.resourceFilePath("dockstore.yml"));

    @Before
    public void clearDB() throws Exception {
        clearState();
        spy(Client.class);

        final UsersApi userApiMock = mock(UsersApi.class);
        when(userApiMock.getUser()).thenReturn(new User());
        whenNew(UsersApi.class).withAnyArguments().thenReturn(userApiMock);

        File sourceFile = new File(ResourceHelpers.resourceFilePath("dockstore-tool-linux-sort.cwl"));
        final String sourceFileContents = FileUtils.readFileToString(sourceFile);
        SourceFile file = mock(SourceFile.class);
        when(file.getContent()).thenReturn(sourceFileContents);
        doReturn(file).when(Client.class, "getCWLFromServer", "quay.io/collaboratory/dockstore-tool-linux-sort");
    }

    @Test
    public void runLaunchOneJson() throws IOException, ApiException {
        replayAll();
        Client.main(new String[] { "--config", ClientIT.getConfigFileLocation(true), "launch", "--entry",
            "quay.io/collaboratory/dockstore-tool-linux-sort", "--json", ResourceHelpers.resourceFilePath("testOneRun.json") });
        verifyAll();
    }

    @Test
    public void runLaunchNJson() throws IOException {
        replayAll();
        Client.main(new String[] { "--config", ClientIT.getConfigFileLocation(true), "launch", "--entry",
                "quay.io/collaboratory/dockstore-tool-linux-sort", "--json", ResourceHelpers.resourceFilePath("testMultipleRun.json") });
        verifyAll();
    }

    @Test
    public void runLaunchTSV() throws IOException {
        replayAll();
        Client.main(new String[] { "--config", ClientIT.getConfigFileLocation(true), "launch", "--entry",
                "quay.io/collaboratory/dockstore-tool-linux-sort", "--tsv", ResourceHelpers.resourceFilePath("testMultipleRun.tsv") });
        verifyAll();
    }
}
