package io.dockstore.wdlparser;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import scala.Option;
import scala.collection.JavaConverters;
import womtool.WomtoolMain;

/**
 * Handler for requests to Lambda function.
 */
public class App implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    ObjectMapper mapper = new ObjectMapper();
    public APIGatewayProxyResponseEvent handleRequest(final APIGatewayProxyRequestEvent input, final Context context) {

        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("X-Custom-Header", "application/json");

        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent()
                .withHeaders(headers);
        if (input != null && input.getBody() != null) {
            try {
                Request request = mapper.readValue(input.getBody(), Request.class);
                try {
                    String s = parseWDLFile(request.getUri(), request.getBranch(), request.getDescriptorRelativePathInGit());
                    return response.withStatusCode(HttpURLConnection.HTTP_OK).withBody(s);
                } catch (IOException e) {
                    e.printStackTrace();
                    return response.withBody("Could not clone repository to temporary directory").withStatusCode(HttpURLConnection.HTTP_INTERNAL_ERROR);
                } catch (GitAPIException e) {
                    e.printStackTrace();
                    return response.withBody("Could not clone Git repository").withStatusCode(HttpURLConnection.HTTP_INTERNAL_ERROR);
                }
            } catch (JsonProcessingException e) {
                e.printStackTrace();
                return response.withBody("Could not process request").withStatusCode(HttpURLConnection.HTTP_BAD_REQUEST);
            }
        } else {
            return response.withBody("No body in request").withStatusCode(HttpURLConnection.HTTP_BAD_REQUEST);
        }
    }

    private String parseWDLFile(String uri, String branch, String descriptorRelativePathInGit) throws IOException, GitAPIException {
        Path tempDirWithPrefix = Files.createTempDirectory("clonedRepository");
        Git git = Git.cloneRepository().setURI(uri).setDirectory(tempDirWithPrefix.toFile()).call();
        git.checkout().setName(branch).call();
        Path descriptorAbsolutePath = tempDirWithPrefix.resolve(descriptorRelativePathInGit);
        String descriptorAbsolutePathString = descriptorAbsolutePath.toString();
        Response response = getResponse(descriptorAbsolutePathString);
        response.getSecondaryFilePaths().replaceAll(s -> s.replaceFirst(tempDirWithPrefix.toString(), ""));
        return mapper.writeValueAsString(response);
    }

    private void handleSuccessResponse(Response response, List<String> strings) {
        strings.remove(0);
        strings.remove(0);
        response.setSecondaryFilePaths(strings);
    }

    public Response getResponse(String descriptorAbsolutePathString) throws IOException {
        Response response = new Response();
        response.setClonedRepositoryAbsolutePath(descriptorAbsolutePathString);
        List<String> commandLineArgs = Arrays.asList("validate", "-l", descriptorAbsolutePathString);
        try {
            WomtoolMain.Termination termination = WomtoolMain
                    .runWomtool(JavaConverters.collectionAsScalaIterableConverter(commandLineArgs).asScala().toSeq());
        Option<String> stdout = termination.stdout();
        // The womtool successful response returns lines of text.
        String[] split = stdout.get().split(System.lineSeparator());
        List<String> strings = new ArrayList<>();
        Collections.addAll(strings, split);

        // The first two lines aren't actual paths.
        if (strings.get(0).equals("Success!") && strings.get(1).equals("List of Workflow dependencies is:")) {
            response.setValid(true);
            handleSuccessResponse(response, strings);
            return response;
        } else {
            response.setValid(false);
            throw new IOException();
        }
        } catch (StackOverflowError e) {
            response.setValid(false);
            return response;
        }
    }
}
