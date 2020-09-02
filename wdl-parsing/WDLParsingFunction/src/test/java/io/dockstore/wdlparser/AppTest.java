package io.dockstore.wdlparser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import javax.ws.rs.core.MediaType;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openapitools.client.model.LanguageParsingRequest;
import org.openapitools.client.model.LanguageParsingResponse;

public class AppTest {
  @Test
  public void successfulResponse() throws IOException {
    ObjectMapper objectMapper = new ObjectMapper();
    LanguageParsingRequest request = new LanguageParsingRequest();
    request.setBranch("1.0.4");
    request.setUri("https://github.com/briandoconnor/dockstore-tool-md5sum.git");
    request.setDescriptorRelativePathInGit("Dockstore.wdl");
    App app = new App();
    APIGatewayProxyRequestEvent requestEvent = new APIGatewayProxyRequestEvent();
    requestEvent.setBody(objectMapper.writeValueAsString(request));
    APIGatewayProxyResponseEvent result = app.handleRequest(requestEvent, null);
    System.out.println(result.getBody());
    assertEquals(HttpURLConnection.HTTP_OK, result.getStatusCode().intValue());
    assertEquals(MediaType.APPLICATION_JSON, result.getHeaders().get("Content-Type"));
    String content = result.getBody();
    assertNotNull(content);
    LanguageParsingResponse response =
        objectMapper.readValue(content, LanguageParsingResponse.class);
    assertNotNull(response.getValid());
    assertTrue(response.getValid());
    assertNotNull(response.getClonedRepositoryAbsolutePath());
    assertTrue(response.getClonedRepositoryAbsolutePath().contains("/tmp"));
    assertNotNull(response.getSecondaryFilePaths());
    assertEquals(0, response.getSecondaryFilePaths().size());
    System.out.println(response.getClonedRepositoryAbsolutePath());
  }

  @Test
  public void successfulResponseOfComplexWorkflow() throws IOException {
    ObjectMapper objectMapper = new ObjectMapper();
    LanguageParsingRequest request = new LanguageParsingRequest();
    request.setBranch("dockstore-test");
    request.setUri("https://github.com/dockstore-testing/gatk-sv-clinical.git");
    request.setDescriptorRelativePathInGit("GATKSVPipelineClinical.wdl");
    App app = new App();
    APIGatewayProxyRequestEvent requestEvent = new APIGatewayProxyRequestEvent();
    requestEvent.setBody(objectMapper.writeValueAsString(request));
    APIGatewayProxyResponseEvent result = app.handleRequest(requestEvent, null);
    System.out.println(result.getBody());
    assertEquals(HttpURLConnection.HTTP_OK, result.getStatusCode().intValue());
    assertEquals(MediaType.APPLICATION_JSON, result.getHeaders().get("Content-Type"));
    String content = result.getBody();
    assertNotNull(content);
    LanguageParsingResponse response =
        objectMapper.readValue(content, LanguageParsingResponse.class);
    assertNotNull(response.getValid());
    assertTrue(response.getValid());
    assertNotNull(response.getClonedRepositoryAbsolutePath());
    assertTrue(response.getClonedRepositoryAbsolutePath().contains("/tmp"));
    assertNotNull(response.getSecondaryFilePaths());
    assertFalse(
        response.getSecondaryFilePaths().contains("GATKSVPipelineClinical.wdl"),
        "Main descriptor isn't a secondary file path");
    assertEquals(76, response.getSecondaryFilePaths().size());
    System.out.println(response.getClonedRepositoryAbsolutePath());
  }

  @Disabled("Too dangerous test to run, also flakey")
  public void testRecursiveWDL() throws IOException {
    File file = new File("src/test/resources/recursive.wdl");
    String path = file.getAbsolutePath();
    LanguageParsingResponse response = App.getResponse(path);
    assertNotNull(response.getValid());
    assertFalse(response.getValid(), "A workflow that has recursive HTTP imports is invalid");
  }
}
