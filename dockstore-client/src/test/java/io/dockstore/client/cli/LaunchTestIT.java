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
import io.dockstore.client.cli.nested.WorkflowClient;
import io.dropwizard.testing.ResourceHelpers;
import io.swagger.client.api.ContainersApi;
import io.swagger.client.api.UsersApi;
import io.swagger.client.api.WorkflowsApi;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemOutRule;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import static io.dockstore.client.cli.ArgumentUtility.CWL_STRING;
import static io.dockstore.client.cli.ArgumentUtility.WDL_STRING;
import static org.mockito.Mockito.mock;

public class LaunchTestIT {
    //create tests that will call client.checkEntryFile for workflow launch with different files and descriptor

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog();
    @Rule
    public final ExpectedSystemExit exit = ExpectedSystemExit.none();

    @Test
    public void wdlCorrect() throws IOException {
        //Test when content and extension are wdl  --> no need descriptor
        File helloWDL = new File(ResourceHelpers.resourceFilePath("hello.wdl"));
        File helloJSON = new File(ResourceHelpers.resourceFilePath("hello.json"));

        ArrayList<String> args = new ArrayList<String>() {{
            add("--local-entry");
            add("--json");
            add(helloJSON.getAbsolutePath());
        }};

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(helloWDL.getAbsolutePath(), args, null);

        Assert.assertTrue("output should include a successful cromwell run",systemOutRule.getLog().contains("Cromwell exit code: 0") );
    }

    @Test
    public void cwlCorrect() throws IOException{
        //Test when content and extension are cwl  --> no need descriptor

        File cwlFile = new File(ResourceHelpers.resourceFilePath("1st-workflow.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("1st-workflow-job.json"));

        ArrayList<String> args = new ArrayList<String>() {{
            add("--local-entry");
            add("--json");
            add(cwlJSON.getAbsolutePath());
        }};

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        // do not use a cache
        runWorkflow(cwlFile, args, api, usersApi, client, false);
    }

    @Test
    public void yamlToolCorrect() throws IOException{
        File cwlFile = new File(ResourceHelpers.resourceFilePath("1st-tool.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("echo-job.yml"));

        ArrayList<String> args = new ArrayList<String>() {{
            add("--local-entry");
            add("--yaml");
            add(cwlJSON.getAbsolutePath());
        }};

        ContainersApi api = mock(ContainersApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        // do not use a cache
        runTool(cwlFile, args, api, usersApi, client, false);
    }

    @Test
    public void cwlCorrectWithCache() throws IOException{
        //Test when content and extension are cwl  --> no need descriptor

        File cwlFile = new File(ResourceHelpers.resourceFilePath("1st-workflow.cwl"));
        File cwlJSON = new File(ResourceHelpers.resourceFilePath("1st-workflow-job.json"));

        ArrayList<String> args = new ArrayList<String>() {{
            add("--local-entry");
            add("--json");
            add(cwlJSON.getAbsolutePath());
        }};

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        // use a cache
        runWorkflow(cwlFile, args, api, usersApi, client, true);
    }

    private void runTool(File cwlFile, ArrayList<String> args, ContainersApi api, UsersApi usersApi, Client client, boolean useCache) {
        client.setConfigFile(ResourceHelpers.resourceFilePath(useCache ? "config.withCache" : "config"));

        ToolClient toolClient = new ToolClient(api, null, usersApi, client, false);
        toolClient.checkEntryFile(cwlFile.getAbsolutePath(), args, null);

        Assert.assertTrue("output should include a successful cwltool run",systemOutRule.getLog().contains("Final process status is success") );
    }

    private void runWorkflow(File cwlFile, ArrayList<String> args, WorkflowsApi api, UsersApi usersApi, Client client, boolean useCache) {
        client.setConfigFile(ResourceHelpers.resourceFilePath(useCache ? "config.withCache" : "config"));

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(cwlFile.getAbsolutePath(), args, null);

        Assert.assertTrue("output should include a successful cwltool run",systemOutRule.getLog().contains("Final process status is success") );
    }

    @Test
    public void cwlWrongExt() throws IOException{
        //Test when content = cwl but ext = wdl, ask for descriptor

        File file = new File(ResourceHelpers.resourceFilePath("wrongExtcwl.wdl"));
        File json = new File(ResourceHelpers.resourceFilePath("hello.json"));

        ArrayList<String> args = new ArrayList<String>() {{
            add("--local-entry");
            add("--json");
            add(json.getAbsolutePath());
        }};

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(file.getAbsolutePath(), args, null);

        Assert.assertTrue("output should include a successful cromwell run",systemOutRule.getLog().contains("Entry file is ambiguous, please re-enter command with '--descriptor <descriptor>' at the end") );
    }

    @Test
    public void cwlWrongExtForce() throws IOException{
        //Test when content = cwl but ext = wdl, descriptor provided --> CWL

        File file = new File(ResourceHelpers.resourceFilePath("wrongExtcwl.wdl"));
        File json = new File(ResourceHelpers.resourceFilePath("1st-workflow-job.json"));

        ArrayList<String> args = new ArrayList<String>() {{
            add("--entry");
            add("wrongExtcwl.wdl");
            add("--local-entry");
            add("--json");
            add(json.getAbsolutePath());
            add("--descriptor");
            add(CWL_STRING);
        }};

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(file.getAbsolutePath(), args, CWL_STRING);

        Assert.assertTrue("output should include a successful cromwell run",systemOutRule.getLog().contains("This is a CWL file.. Please put the correct extension to the entry file name.") );
    }

    @Test
    public void wdlWrongExt() throws IOException{
        //Test when content = wdl but ext = cwl, ask for descriptor

        File file = new File(ResourceHelpers.resourceFilePath("wrongExtwdl.cwl"));
        File json = new File(ResourceHelpers.resourceFilePath("hello.json"));

        ArrayList<String> args = new ArrayList<String>() {{
            add("--local-entry");
            add("--json");
            add(json.getAbsolutePath());
        }};

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(file.getAbsolutePath(), args, null);

        Assert.assertTrue("output should include a successful cromwell run",systemOutRule.getLog().contains("Entry file is ambiguous, please re-enter command with '--descriptor <descriptor>' at the end") );
    }

    @Test
    public void randomExtCwl() throws IOException{
        //Test when content is random, but ext = cwl
        File file = new File(ResourceHelpers.resourceFilePath("random.cwl"));
        File json = new File(ResourceHelpers.resourceFilePath("hello.json"));

        ArrayList<String> args = new ArrayList<String>() {{
            add("--local-entry");
            add("--json");
            add(json.getAbsolutePath());
        }};

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(file.getAbsolutePath(), args, null);

        Assert.assertTrue("output should include a successful cromwell run",systemOutRule.getLog().contains("Entry file is ambiguous, please re-enter command with '--descriptor <descriptor>' at the end") );
    }

    @Test
    public void randomExtWdl() throws IOException{
        //Test when content is random, but ext = wdl
        File file = new File(ResourceHelpers.resourceFilePath("random.wdl"));
        File json = new File(ResourceHelpers.resourceFilePath("hello.json"));

        ArrayList<String> args = new ArrayList<String>() {{
            add("--local-entry");
            add("--json");
            add(json.getAbsolutePath());
        }};

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(file.getAbsolutePath(), args, null);

        Assert.assertTrue("output should include a successful cromwell run",systemOutRule.getLog().contains("Entry file is ambiguous, please re-enter command with '--descriptor <descriptor>' at the end") );
    }

    @Test
    public void wdlWrongExtForce() throws IOException{
        //Test when content = wdl but ext = cwl, descriptor provided --> WDL

        File file = new File(ResourceHelpers.resourceFilePath("wrongExtwdl.cwl"));
        File json = new File(ResourceHelpers.resourceFilePath("hello.json"));

        ArrayList<String> args = new ArrayList<String>() {{
            add("--entry");
            add(file.getAbsolutePath());
            add("--local-entry");
            add("--json");
            add(json.getAbsolutePath());
            add("--descriptor");
            add(WDL_STRING);
        }};

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(file.getAbsolutePath(), args, WDL_STRING);

        Assert.assertTrue("output should include a successful cromwell run",systemOutRule.getLog().contains("This is a WDL file.. Please put the correct extension to the entry file name.") );
    }

    @Test
    public void cwlWrongExtForce1() throws IOException{
        //Test when content = cwl but ext = wdl, descriptor provided --> !CWL

        File file = new File(ResourceHelpers.resourceFilePath("wrongExtcwl.wdl"));
        File json = new File(ResourceHelpers.resourceFilePath("hello.json"));

        ArrayList<String> args = new ArrayList<String>() {{
            add("--entry");
            add("wrongExtcwl.wdl");
            add("--local-entry");
            add("--json");
            add(json.getAbsolutePath());
            add("--descriptor");
            add(WDL_STRING);
        }};

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));

        exit.expectSystemExit();

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(file.getAbsolutePath(), args, WDL_STRING);
    }

    @Test
    public void wdlWrongExtForce1() throws IOException{
        //Test when content = wdl but ext = cwl, descriptor provided --> !WDL

        File file = new File(ResourceHelpers.resourceFilePath("wrongExtwdl.cwl"));
        File json = new File(ResourceHelpers.resourceFilePath("hello.json"));

        ArrayList<String> args = new ArrayList<String>() {{
            add("--entry");
            add("wrongExtwdl.cwl");
            add("--local-entry");
            add("--json");
            add(json.getAbsolutePath());
            add("--descriptor");
            add(CWL_STRING);
        }};

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));

        exit.expectSystemExit();

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(file.getAbsolutePath(), args, CWL_STRING);
    }

    @Test
    public void cwlNoExt() throws IOException{
    //Test when content = cwl but no ext

        File file = new File(ResourceHelpers.resourceFilePath("cwlNoExt"));
        File json = new File(ResourceHelpers.resourceFilePath("1st-workflow-job.json"));

        ArrayList<String> args = new ArrayList<String>() {{
            add("--entry");
            add("cwlNoExt");
            add("--local-entry");
            add("--json");
            add(json.getAbsolutePath());
        }};

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(file.getAbsolutePath(), args, null);

        Assert.assertTrue("output should contain a validation issue",systemOutRule.getLog().contains("This is a CWL file.. Please put an extension to the entry file name.") );
    }

    @Test
    public void wdlNoExt() throws IOException{
        //Test when content = wdl but no ext

        File file = new File(ResourceHelpers.resourceFilePath("wdlNoExt"));
        File json = new File(ResourceHelpers.resourceFilePath("hello.json"));

        ArrayList<String> args = new ArrayList<String>() {{
            add("--entry");
            add(file.getAbsolutePath());
            add("--local-entry");
            add("--json");
            add(json.getAbsolutePath());
        }};

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(file.getAbsolutePath(), args, null);

        Assert.assertTrue("output should include a successful cromwell run",systemOutRule.getLog().contains("This is a WDL file.. Please put an extension to the entry file name.") );

    }

    @Test
    public void randomNoExt() throws IOException{
        //Test when content is neither CWL nor WDL, and there is no extension

        File file = new File(ResourceHelpers.resourceFilePath("random"));
        File json = new File(ResourceHelpers.resourceFilePath("hello.json"));

        ArrayList<String> args = new ArrayList<String>() {{
            add("--entry");
            add(file.getAbsolutePath());
            add("--local-entry");
            add("--json");
            add(json.getAbsolutePath());
        }};

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));

        exit.expectSystemExit();

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(file.getAbsolutePath(), args, null);

        Assert.assertTrue("output should include an error message of invalid file",systemOutRule.getLog().contains("Entry file is invalid. Please enter a valid CWL/WDL file with the correct extension on the file name."));

    }

    @Test
    public void randomWithExt() throws IOException{
        //Test when content is neither CWL nor WDL, and there is no extension

        File file = new File(ResourceHelpers.resourceFilePath("hello.txt"));
        File json = new File(ResourceHelpers.resourceFilePath("hello.json"));

        ArrayList<String> args = new ArrayList<String>() {{
            add("--entry");
            add(file.getAbsolutePath());
            add("--local-entry");
            add("--json");
            add(json.getAbsolutePath());
        }};

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));

        exit.expectSystemExit();

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(file.getAbsolutePath(), args, null);

        Assert.assertTrue("output should include an error message of invalid file",systemOutRule.getLog().contains("Entry file is invalid. Please enter a valid CWL/WDL file with the correct extension on the file name."));

    }

    @Test
    public void wdlNoTask() throws IOException{
        //Test when content is missing 'task'

        File file = new File(ResourceHelpers.resourceFilePath("noTask.wdl"));
        File json = new File(ResourceHelpers.resourceFilePath("hello.json"));

        ArrayList<String> args = new ArrayList<String>() {{
            add("--entry");
            add(file.getAbsolutePath());
            add("--local-entry");
            add("--json");
            add(json.getAbsolutePath());
        }};

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));

        exit.expectSystemExit();

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(file.getAbsolutePath(), args, null);

        Assert.assertTrue("output should include an error message and exit",systemOutRule.getLog().contains("Required fields that are missing from WDL file : 'task'"));

    }

    @Test
    public void wdlNoCommand() throws IOException{
        //Test when content is missing 'command'

        File file = new File(ResourceHelpers.resourceFilePath("noCommand.wdl"));
        File json = new File(ResourceHelpers.resourceFilePath("hello.json"));

        ArrayList<String> args = new ArrayList<String>() {{
            add("--entry");
            add(file.getAbsolutePath());
            add("--local-entry");
            add("--json");
            add(json.getAbsolutePath());
        }};

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));

        exit.expectSystemExit();

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(file.getAbsolutePath(), args, null);

        Assert.assertTrue("output should include an error message and exit",systemOutRule.getLog().contains("Required fields that are missing from WDL file : 'command'"));

    }

    @Test
    public void wdlNoWfCall() throws IOException{
        //Test when content is missing 'workflow' and 'call'

        File file = new File(ResourceHelpers.resourceFilePath("noWfCall.wdl"));
        File json = new File(ResourceHelpers.resourceFilePath("hello.json"));

        ArrayList<String> args = new ArrayList<String>() {{
            add("--entry");
            add(file.getAbsolutePath());
            add("--local-entry");
            add("--json");
            add(json.getAbsolutePath());
        }};

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));

        exit.expectSystemExit();

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(file.getAbsolutePath(), args, null);

        Assert.assertTrue("output should include an error message and exit",systemOutRule.getLog().contains("Required fields that are missing from WDL file : 'workflow' 'call'"));

    }

    @Test
    public void cwlNoInput() throws IOException{
        //Test when content is missing 'input'

        File file = new File(ResourceHelpers.resourceFilePath("noInput.cwl"));
        File json = new File(ResourceHelpers.resourceFilePath("1st-workflow-job.json"));

        ArrayList<String> args = new ArrayList<String>() {{
            add("--entry");
            add(file.getAbsolutePath());
            add("--local-entry");
            add("--json");
            add(json.getAbsolutePath());
        }};

        WorkflowsApi api = mock(WorkflowsApi.class);
        UsersApi usersApi = mock(UsersApi.class);
        Client client = new Client();
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));

        exit.expectSystemExit();

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client, false);
        workflowClient.checkEntryFile(file.getAbsolutePath(), args, null);

        Assert.assertTrue("output should include an error message and exit",systemOutRule.getLog().contains("Required fields that are missing from CWL file : 'inputs'"));

    }

}
