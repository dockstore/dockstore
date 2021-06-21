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
    // TODO: Make this configurable
    public static final String LANGUAGE_PARSER_ENDPOINT = "http://localhost:3000/parse";
    private static final Logger LOGGER = LoggerFactory.getLogger(LanguageParserHelper.class);
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private LanguageParserHelper() {

    }

    /**
     * Send a sync request to lambda. A valid language parsing response is expected.
     *
     * @param languageParsingRequest The request to send to lambda
     * @return  A valid language parsing response from the lambda
     * @throws InterruptedException An unexpected exception
     * @throws IOException An unexpected exception
     */
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

    /**
     * Send an async request to lambda. Fire and forget. Timeout longer than 1 second is expected (lambda is going to take a while to run), all other exceptions are not.
     * @param languageParsingRequest    The request to send to lambda
     * @throws ExecutionException       An unexpected exception
     * @throws InterruptedException     An unexpected exception
     * @throws JsonProcessingException  An unexpected exception
     */
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
        return HttpRequest.newBuilder().uri(URI.create(LANGUAGE_PARSER_ENDPOINT)).POST(bodyPublisher).build();
    }
}
