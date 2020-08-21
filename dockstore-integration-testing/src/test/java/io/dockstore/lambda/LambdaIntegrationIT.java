package io.dockstore.lambda;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import javax.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

public class LambdaIntegrationIT {
    @Test
    public void testLambdaIntegration() throws IOException, InterruptedException {
        HttpClient httpClient = HttpClient.newHttpClient();
        // Cannot use swagger client, even the lambda doesn't know which endpoint it is
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create("http://localhost:3000/parse")).build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        Assert.assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.statusCode());
        Assert.assertEquals("No body in request", response.body());
    }
}
