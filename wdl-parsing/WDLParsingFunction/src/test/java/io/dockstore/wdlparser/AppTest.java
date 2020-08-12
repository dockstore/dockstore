package io.dockstore.wdlparser;

import java.io.IOException;
import java.net.HttpURLConnection;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class AppTest {
  @Test
  public void successfulResponse() throws JsonProcessingException {
    ObjectMapper objectMapper = new ObjectMapper();
    Request request = new Request();
    request.setBranch("1.0.4");
    request.setUri("https://github.com/briandoconnor/dockstore-tool-md5sum.git");
    request.setDescriptorRelativePathInGit("Dockstore.wdl");
    App app = new App();
    APIGatewayProxyRequestEvent requestEvent = new APIGatewayProxyRequestEvent();
    requestEvent.setBody(objectMapper.writeValueAsString(request));
    APIGatewayProxyResponseEvent result = app.handleRequest(requestEvent, null);
    assertEquals(HttpURLConnection.HTTP_OK, result.getStatusCode().intValue());
    assertEquals(result.getHeaders().get("Content-Type"), "application/json");
    String content = result.getBody();
    assertNotNull(content);
    Response response = objectMapper.readValue(content, Response.class);
    assertTrue(response.isValid());
    assertTrue(response.getClonedRepositoryAbsolutePath().contains("/tmp"));
    assertEquals(0, response.getSecondaryFilePaths().size());
    System.out.println(response.getClonedRepositoryAbsolutePath());
  }

  @Test
  public void successfulResponseOfComplexWorkflow() throws JsonProcessingException {
    ObjectMapper objectMapper = new ObjectMapper();
    Request request = new Request();
    request.setBranch("dockstore-test");
    request.setUri("https://github.com/dockstore-testing/gatk-sv-clinical.git");
    request.setDescriptorRelativePathInGit("GATKSVPipelineClinical.wdl");
    App app = new App();
    APIGatewayProxyRequestEvent requestEvent = new APIGatewayProxyRequestEvent();
    requestEvent.setBody(objectMapper.writeValueAsString(request));
    APIGatewayProxyResponseEvent result = app.handleRequest(requestEvent, null);
    assertEquals(HttpURLConnection.HTTP_OK, result.getStatusCode().intValue());
    assertEquals(result.getHeaders().get("Content-Type"), "application/json");
    String content = result.getBody();
    assertNotNull(content);
    Response response = objectMapper.readValue(content, Response.class);
    assertTrue(response.isValid());
    assertTrue(response.getClonedRepositoryAbsolutePath().contains("/tmp"));
    assertTrue(response.getSecondaryFilePaths().contains("GATKSVPipelineClinical.wdl"));
    assertEquals(76, response.getSecondaryFilePaths().size());
    System.out.println(response.getClonedRepositoryAbsolutePath());
  }

  @Test
  public void testRecursiveWDL() throws IOException {
    String path = ClassLoader.getSystemClassLoader().getResource("recursive.wdl").getPath();
    App app = new App();
    app.getResponse(path);
  }
}
