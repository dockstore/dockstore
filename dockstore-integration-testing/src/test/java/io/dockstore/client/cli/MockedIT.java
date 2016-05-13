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

import io.dockstore.client.cli.nested.ToolClient;
import io.dockstore.common.TestUtility;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dropwizard.testing.ResourceHelpers;
import io.dropwizard.testing.junit.DropwizardAppRule;
import io.swagger.client.ApiException;
import io.swagger.client.api.UsersApi;
import io.swagger.client.model.SourceFile;
import io.swagger.client.model.User;
import io.swagger.quay.client.api.UserApi;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.File;
import java.io.IOException;

import static io.dockstore.common.CommonTestUtilities.clearState;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.doNothing;
import static org.powermock.api.mockito.PowerMockito.spy;
import static org.powermock.api.mockito.PowerMockito.whenNew;

/**
 * These tests use mocking to simulate responses from GitHub, BitBucket, and Quay.io
 * 
 * @author dyuen
 */
@PowerMockIgnore("javax.management.*")
@RunWith(PowerMockRunner.class)
@PrepareForTest({Client.class, ToolClient.class, UserApi.class})
public class MockedIT {

    @ClassRule
    public static final DropwizardAppRule<DockstoreWebserviceConfiguration> RULE = new DropwizardAppRule<>(
            DockstoreWebserviceApplication.class, ResourceHelpers.resourceFilePath("dockstore.yml"));

    @Before
    public void clearDB() throws Exception {
        clearState();
        Client client = mock(Client.class);
        ToolClient toolClient = spy(new ToolClient(client));

        final UsersApi userApiMock = mock(UsersApi.class);
        when(client.getConfigFile()).thenReturn(TestUtility.getConfigFileLocation(true));
        when(userApiMock.getUser()).thenReturn(new User());
        whenNew(UsersApi.class).withAnyArguments().thenReturn(userApiMock);
        whenNew(ToolClient.class).withAnyArguments().thenReturn(toolClient);

        // mock return of a simple CWL file
        File sourceFile = new File(ResourceHelpers.resourceFilePath("dockstore-tool-linux-sort.cwl"));
        final String sourceFileContents = FileUtils.readFileToString(sourceFile);
        SourceFile file = mock(SourceFile.class);
        when(file.getContent()).thenReturn(sourceFileContents);
        doReturn(file).when(toolClient).getDescriptorFromServer("quay.io/collaboratory/dockstore-tool-linux-sort", "cwl");
        doNothing().when(toolClient).downloadDescriptors(anyString(),anyString(),anyObject());

        // mock return of a more complicated CWL file
        File sourceFileArrays = new File(ResourceHelpers.resourceFilePath("arrays.cwl"));
        final String sourceFileArraysContents = FileUtils.readFileToString(sourceFileArrays);
        SourceFile file2 = mock(SourceFile.class);
        when(file2.getContent()).thenReturn(sourceFileArraysContents);
        doReturn(file2).when(toolClient).getDescriptorFromServer("quay.io/collaboratory/arrays", "cwl");

        FileUtils.deleteQuietly(new File("/tmp/wc1.out"));
        FileUtils.deleteQuietly(new File("/tmp/wc2.out"));
        FileUtils.deleteQuietly(new File("/tmp/example.bedGraph"));

        try {
            FileUtils.copyFile(new File(ResourceHelpers.resourceFilePath("example.bedGraph")), new File("./datastore/example.bedGraph"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @After
    public void clearFiles(){
        FileUtils.deleteQuietly(new File("/tmp/wc1.out"));
        FileUtils.deleteQuietly(new File("/tmp/wc2.out"));
        FileUtils.deleteQuietly(new File("/tmp/example.bedGraph"));
    }

    @Test
    public void runLaunchOneJson() throws IOException, ApiException {
        Client.main(new String[] { "--config", TestUtility.getConfigFileLocation(true), "tool", "launch", "--entry",
            "quay.io/collaboratory/dockstore-tool-linux-sort", "--json", ResourceHelpers.resourceFilePath("testOneRun.json") });
    }

    @Test
    public void runLaunchNJson() throws IOException {
        Client.main(new String[] { "--config", TestUtility.getConfigFileLocation(true), "tool", "launch", "--entry",
                "quay.io/collaboratory/dockstore-tool-linux-sort", "--json", ResourceHelpers.resourceFilePath("testMultipleRun.json") });
    }

    @Test
    public void runLaunchTSV() throws IOException {
        Client.main(new String[] { "--config", TestUtility.getConfigFileLocation(true), "tool", "launch", "--entry",
                "quay.io/collaboratory/dockstore-tool-linux-sort", "--tsv", ResourceHelpers.resourceFilePath("testMultipleRun.tsv") });
    }

    /**
     * Tests local file input in arrays or as single files, output to local file
     * @throws IOException
     * @throws ApiException
     */
    @Test
    public void runLaunchOneLocalArrayedJson() throws IOException, ApiException {
        Client.main(new String[] { "--config", TestUtility.getConfigFileLocation(true), "tool", "launch", "--entry",
            "quay.io/collaboratory/arrays", "--json", ResourceHelpers.resourceFilePath("testArrayLocalInputLocalOutput.json") });

        Assert.assertTrue(new File("/tmp/example.bedGraph").exists());
    }

    /**
     * Tests http file input in arrays or as single files, output to local file and local array
     * @throws IOException
     * @throws ApiException
     */
    @Test
    public void runLaunchOneHTTPArrayedJson() throws IOException, ApiException {
        System.out.println(TestUtility.getConfigFileLocation(true));
        Client.main(new String[] { "--config", TestUtility.getConfigFileLocation(true), "tool", "launch", "--entry",
            "quay.io/collaboratory/arrays", "--json", ResourceHelpers.resourceFilePath("testArrayHttpInputLocalOutput.json") });

        Assert.assertTrue(new File("/tmp/wc1.out").exists());
        Assert.assertTrue(new File("/tmp/wc2.out").exists());
        Assert.assertTrue(new File("/tmp/example.bedGraph").exists());
    }
}
