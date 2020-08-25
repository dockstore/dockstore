package io.dockstore.webservice.helpers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dockstore.webservice.core.LanguageParsingRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LanguageParserHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(LanguageParserHelper.class);
    private LanguageParserHelper() {

    }
    public static void sendToLambda() throws ExecutionException, InterruptedException, JsonProcessingException {
        HttpClient httpClient = HttpClient.newHttpClient();
        ObjectMapper objectMapper = new ObjectMapper();
        String requestBody = objectMapper.writeValueAsString(new LanguageParsingRequest());

        HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.ofString(requestBody);
        // Cannot use swagger client, even the lambda doesn't know which endpoint it is
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create("http://localhost:3000/parse")).POST(bodyPublisher).build();
        try {
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(HttpResponse::body).get(1, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            LOGGER.debug("");

        }
    }
}
