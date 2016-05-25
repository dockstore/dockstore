package io.dockstore.client.cli;

import io.dockstore.client.cli.nested.WorkflowClient;
import io.dropwizard.testing.ResourceHelpers;
import io.swagger.client.api.UsersApi;
import io.swagger.client.api.WorkflowsApi;
import org.junit.Assert;
import org.junit.Ignore;
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

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client);
        workflowClient.checkEntryFile(helloWDL.getAbsolutePath(), args, null);

        Assert.assertTrue("output should include a successful cromwell run",systemOutRule.getLog().contains("Cromwell exit code: 0") );
    }

    //This test will be ignored for now because of cwltool and cwl file provisioning problem
    @Ignore
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
        client.setConfigFile(ResourceHelpers.resourceFilePath("config"));

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client);
        workflowClient.checkEntryFile(cwlFile.getAbsolutePath(), args, null);

        Assert.assertTrue("output should include a successful cromwell run",systemOutRule.getLog().contains("Cromwell exit code: 0") );
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

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client);
        workflowClient.checkEntryFile(file.getAbsolutePath(), args, null);

        Assert.assertTrue("output should include a successful cromwell run",systemOutRule.getLog().contains("Entry file is ambiguous, please re-enter command with '--descriptor <descriptor>' at the end") );
    }

    //This test will be ignored for now because of cwltool and cwl file provisioning problem
    @Ignore
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

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client);
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

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client);
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

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client);
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

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client);
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

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client);
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

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client);
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

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client);
        workflowClient.checkEntryFile(file.getAbsolutePath(), args, CWL_STRING);
    }

    //This test will be ignored for now because of cwltool and cwl file provisioning problem
    @Ignore
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

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client);
        workflowClient.checkEntryFile(file.getAbsolutePath(), args, null);

        Assert.assertTrue("output should include a successful cromwell run",systemOutRule.getLog().contains("This is a CWL file.. Please put an extension to the entry file name.") );
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

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client);
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

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client);
        workflowClient.checkEntryFile(file.getAbsolutePath(), args, null);


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

        WorkflowClient workflowClient = new WorkflowClient(api, usersApi, client);
        workflowClient.checkEntryFile(file.getAbsolutePath(), args, null);

        //error invalid "Entry file is invalid. Please enter a valid CWL/WDL file with the correct extension on the file name."

    }

}
