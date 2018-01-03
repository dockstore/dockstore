package io.dockstore.client.cli.nested;

import io.dockstore.client.cli.Client;
import io.swagger.client.ApiException;
import io.swagger.client.api.ContainersApi;
import io.swagger.client.api.ContainertagsApi;
import io.swagger.client.api.UsersApi;
import io.swagger.client.model.DockstoreTool;
import io.swagger.client.model.SourceFile;
import org.apache.http.HttpStatus;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.mockito.Mockito.when;

public class ToolClientTest {

    private static final String MISSING_TAG = "0.3.1";
    private static final String GOOD_TAG = "0.3.0";
    private static final String REPOSITORY = "registry.hub.docker.com/ucsctreehouse/fusion";
    private static final long CONTAINER_ID = 1L;

    private ContainersApi containersApi;
    private ContainertagsApi containertagsApi;
    private UsersApi usersApi;
    private Client client;
    private DockstoreTool dockstoreTool;
    private ApiException apiException;

    @Before
    public void setup() {
        containersApi = Mockito.mock(ContainersApi.class);
        containertagsApi = Mockito.mock(ContainertagsApi.class);
        usersApi = Mockito.mock(UsersApi.class);
        client = Mockito.mock(Client.class);
        dockstoreTool = Mockito.mock(DockstoreTool.class);
        apiException = Mockito.mock(ApiException.class);
        when(apiException.getCode()).thenReturn(HttpStatus.SC_BAD_REQUEST);
        when(dockstoreTool.getId()).thenReturn(CONTAINER_ID);
        when(containersApi.cwl(CONTAINER_ID, MISSING_TAG)).thenThrow(apiException);
        when(containersApi.cwl(CONTAINER_ID, null)).thenThrow(apiException);
        when(containersApi.cwl(CONTAINER_ID, GOOD_TAG)).thenReturn(Mockito.mock(SourceFile.class));
        when(containersApi
                .getPublishedContainerByToolPath(REPOSITORY))
                .thenReturn(dockstoreTool);
    }

    @Test
    public void getDescriptorFromServer_missingTag()  {
        ToolClient toolClient = new ToolClient(containersApi, containertagsApi, usersApi, client, false);
        boolean exceptionThrown = false;
        try {
            toolClient.getDescriptorFromServer(REPOSITORY + ":" + MISSING_TAG, "cwl");
        }
        catch (Exception ex) {
            exceptionThrown = true;
        }
        Assert.assertTrue(exceptionThrown);
    }

    @Test
    public void getDescriptorFromServer_noTag()  {
        ToolClient toolClient = new ToolClient(containersApi, containertagsApi, usersApi, client, false);
        boolean exceptionThrown = false;
        try {
            toolClient.getDescriptorFromServer(REPOSITORY , "cwl");
        }
        catch (Exception ex) {
            exceptionThrown = true;
        }
        Assert.assertTrue(exceptionThrown);
    }

    @Test
    public void getDescriptorFromServer_goodTag() {
        ToolClient toolClient = new ToolClient(containersApi, containertagsApi, usersApi, client, false);
        SourceFile cwl = toolClient.getDescriptorFromServer(REPOSITORY + ":" + GOOD_TAG, "cwl");
        Assert.assertNotNull(cwl);
    }
}
