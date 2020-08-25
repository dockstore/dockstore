package io.dockstore.lambda;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Test;

public class LambdaIntegrationIT {
    @Test
    public void testSyncLambdaIntegration() throws IOException, InterruptedException {
        HttpClient httpClient = HttpClient.newHttpClient();
        // Cannot use swagger client, even the lambda doesn't know which endpoint it is
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create("http://localhost:3000/parse")).build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        Assert.assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.statusCode());
        Assert.assertEquals("No body in request", response.body());
    }

    @Test
    public void testAsyncLambdaIntegration() throws InterruptedException, ExecutionException, TimeoutException {
        HttpClient httpClient = HttpClient.newHttpClient();
        // Cannot use swagger client, even the lambda doesn't know which endpoint it is
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create("http://localhost:3000/parse")).build();
        try {
            String response = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(HttpResponse::body).get(1, TimeUnit.SECONDS);
            Assert.fail("Should have have finished parsing in 1 second, that's impossible");
        } catch (TimeoutException e) {
            Assert.assertNull("Should have timed out because it takes longer than 1 second", e.getLocalizedMessage());
        }
    }
}
