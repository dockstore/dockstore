package io.dockstore.wdlparser;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.openapitools.client.model.LanguageParsingRequest;
import org.openapitools.client.model.LanguageParsingResponse;
import org.openapitools.client.model.VersionTypeValidation;
import scala.Option;
import scala.collection.JavaConverters;
import womtool.WomtoolMain;

/** Handler for requests to Lambda function. */
public class App
    implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
  ObjectMapper mapper = new ObjectMapper();

  @Override
  public APIGatewayProxyResponseEvent handleRequest(
      final APIGatewayProxyRequestEvent input, final Context context) {

    Map<String, String> headers = new HashMap<>();
    headers.put("Content-Type", "application/json");
    headers.put("X-Custom-Header", "application/json");

    APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent().withHeaders(headers);
    if (input != null && input.getBody() != null) {
      try {
        LanguageParsingRequest request =
            mapper.readValue(input.getBody(), LanguageParsingRequest.class);
        try {
          String s =
              parseWdlFile(
                  request.getUri(),
                  request.getBranch(),
                  request.getDescriptorRelativePathInGit(),
                  request);
          return response.withStatusCode(HttpURLConnection.HTTP_OK).withBody(s);
        } catch (IOException e) {
          e.printStackTrace();
          return response
              .withBody("Could not clone repository to temporary directory")
              .withStatusCode(HttpURLConnection.HTTP_INTERNAL_ERROR);
        } catch (GitAPIException e) {
          StringWriter sw = new StringWriter();
          e.printStackTrace(new PrintWriter(sw));
          String exceptionAsString = sw.toString();
          return response
              .withBody(exceptionAsString)
              .withStatusCode(HttpURLConnection.HTTP_INTERNAL_ERROR);
        }
      } catch (IOException e) {
        e.printStackTrace();
        return response
            .withBody("Could not process request")
            .withStatusCode(HttpURLConnection.HTTP_BAD_REQUEST);
      }
    } else {
      return response
          .withBody("No body in request")
          .withStatusCode(HttpURLConnection.HTTP_BAD_REQUEST);
    }
  }

  private String parseWdlFile(
      String uri,
      String branch,
      String descriptorRelativePathInGit,
      LanguageParsingRequest languageParsingRequest)
      throws IOException, GitAPIException {
    Path tempDirWithPrefix = Files.createTempDirectory("clonedRepository");
    Git.cloneRepository()
        .setCloneAllBranches(false)
        .setBranch(branch)
        .setURI(uri)
        .setDirectory(tempDirWithPrefix.toFile())
        .call();
    Path descriptorAbsolutePath = tempDirWithPrefix.resolve(descriptorRelativePathInGit);
    String descriptorAbsolutePathString = descriptorAbsolutePath.toString();
    LanguageParsingResponse response = getResponse(descriptorAbsolutePathString);
    response.setLanguageParsingRequest(languageParsingRequest);
    if (response.getSecondaryFilePaths() != null) {
      response
          .getSecondaryFilePaths()
          .replaceAll(s -> s.replaceFirst(tempDirWithPrefix.toString(), ""));
    }
    return mapper.writeValueAsString(response);
  }

  // The first two lines aren't actual paths.
  // It looks like "Success!" and "List of Workflow dependencies is:"
  private static void handleSuccessResponse(
      LanguageParsingResponse response, List<String> strings) {
    strings.remove(0);
    strings.remove(0);
    // If there are no imports, womtool says None
    if (strings.get(0).equals("None")) {
      strings.remove(0);
    }
    response.setSecondaryFilePaths(strings);
  }

  /**
   * Get a language parsing response by running womtool.
   *
   * @param descriptorAbsolutePathString Absolute path to the main descriptor file
   * @return LanguageParsingResponse constructed after running womtool
   */
  public static LanguageParsingResponse getResponse(String descriptorAbsolutePathString) {
    LanguageParsingResponse response = new LanguageParsingResponse();
    response.setClonedRepositoryAbsolutePath(descriptorAbsolutePathString);
    List<String> commandLineArgs = Arrays.asList("validate", "-l", descriptorAbsolutePathString);
    try {
      WomtoolMain.Termination termination =
          WomtoolMain.runWomtool(
              JavaConverters.collectionAsScalaIterableConverter(commandLineArgs).asScala().toSeq());
      Option<String> stdout = termination.stdout();
      // The womtool successful response returns lines of text.
      String[] split = stdout.get().split(System.lineSeparator());
      List<String> strings = new ArrayList<>();
      Collections.addAll(strings, split);

      // The first two lines aren't actual paths.
      VersionTypeValidation versionTypeValidation = new VersionTypeValidation();
      if (strings.get(0).equals("Success!")
          && strings.get(1).equals("List of Workflow dependencies is:")) {
        versionTypeValidation.setValid(true);
        response.setVersionTypeValidation(versionTypeValidation);
        handleSuccessResponse(response, strings);
      } else {
        versionTypeValidation.setValid(false);
        response.setVersionTypeValidation(versionTypeValidation);
      }
      return response;
    } catch (StackOverflowError e) {
      VersionTypeValidation versionTypeValidation = new VersionTypeValidation();
      versionTypeValidation.setValid(false);
      response.setVersionTypeValidation(versionTypeValidation);
      return response;
    }
  }
}
