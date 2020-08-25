package io.dockstore.webservice.helpers;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dockstore.webservice.core.languageParsing.LanguageParsingRequest;
import io.dockstore.webservice.core.languageParsing.LanguageParsingResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LanguageParserHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(LanguageParserHelper.class);
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private LanguageParserHelper() {

    }

    public static LanguageParsingResponse sendToLambdaSync(LanguageParsingRequest languageParsingRequest) throws InterruptedException, IOException {
        HttpRequest request = convertLanguageParsingRequestToHttpRequest(languageParsingRequest);
        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == HttpURLConnection.HTTP_OK) {
            LanguageParsingResponse languageParsingResponse = MAPPER.readValue(response.body(), LanguageParsingResponse.class);
            return languageParsingResponse;
        } else {
            LOGGER.error("Language parsing failed.");
            return new LanguageParsingResponse();
        }
    }

    public static void sendToLambdaAsync(LanguageParsingRequest languageParsingRequest) throws ExecutionException, InterruptedException, JsonProcessingException {
        HttpRequest request = convertLanguageParsingRequestToHttpRequest(languageParsingRequest);
        try {
            HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(HttpResponse::body).get(1, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            LOGGER.debug("Sent to language parsing service.");
        }
    }

    private static HttpRequest convertLanguageParsingRequestToHttpRequest(LanguageParsingRequest languageParsingRequest)
            throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        String requestBody = objectMapper.writeValueAsString(languageParsingRequest);
        HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.ofString(requestBody);
        // Cannot use swagger client, even the lambda doesn't know which endpoint it is
        return HttpRequest.newBuilder().uri(URI.create("http://localhost:3000/parse")).POST(bodyPublisher).build();
    }
}
