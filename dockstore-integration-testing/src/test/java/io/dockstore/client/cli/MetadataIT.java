package io.dockstore.client.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.dockstore.client.cli.BaseIT.TestStatus;
import io.dockstore.common.MuteForSuccessfulTests;
import io.dockstore.common.PipHelper;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.ApiException;
import io.dockstore.openapi.client.Pair;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;

@ExtendWith(SystemStubsExtension.class)
@ExtendWith(MuteForSuccessfulTests.class)
@ExtendWith(TestStatus.class)
class MetadataIT extends BaseIT {
    @SystemStub
    public final SystemOut systemOut = new SystemOut();
    @SystemStub
    public final SystemErr systemErr = new SystemErr();

    public ApiClient apiClient = getWebClient();

    public Object get_request(String endpoint, List<Pair> queryParams) {
        return apiClient.invokeAPI(endpoint, "GET", queryParams, null, new HashMap<>(), new HashMap<>(), "application/json",
            "application/json", new String[]{"BEARER"}, null);

    }

    public List<Pair> queryParams() {
        List<Pair> queryParams = new ArrayList<>();
        queryParams.addAll(apiClient.parameterToPairs("", "python_version", "3"));
        queryParams.addAll(apiClient.parameterToPairs("", "runner", "cwltool"));
        queryParams.addAll(apiClient.parameterToPairs("", "output", "json"));
        return queryParams;
    }

    @Test
    void testValidClientVersion() {
        String endpoint = "/metadata/runner_dependencies";
        List<Pair> queryParams = this.queryParams();
        queryParams.addAll(apiClient.parameterToPairs("", "client_version", "1.13.0"));
        this.get_request(endpoint, queryParams);
        assertEquals(HttpStatus.OK_200, apiClient.getStatusCode());
    }

    @Test
    void testPrereleaseClientVersion() {
        String endpoint = "/metadata/runner_dependencies";
        List<Pair> queryParams = this.queryParams();
        queryParams.addAll(apiClient.parameterToPairs("", "client_version", "1.13.0-alpha.7"));
        this.get_request(endpoint, queryParams);
        assertEquals(HttpStatus.OK_200, apiClient.getStatusCode());
    }

    @Test
    void testDevelopmentSemanticVersion() {
        String endpoint = "/metadata/runner_dependencies";
        List<Pair> queryParams = this.queryParams();
        queryParams.addAll(apiClient.parameterToPairs("", "client_version", PipHelper.DEV_SEM_VER));
        this.get_request(endpoint, queryParams);
        assertEquals(HttpStatus.OK_200, apiClient.getStatusCode());
    }

    @Test
    void testInvalidClientVersion() {
        String endpoint = "/metadata/runner_dependencies";
        List<Pair> queryParams = this.queryParams();
        queryParams.addAll(apiClient.parameterToPairs("", "client_version", "1.2"));
        ApiException exception = assertThrows(ApiException.class, () -> this.get_request(endpoint, queryParams));
        assertEquals(HttpStatus.BAD_REQUEST_400, exception.getCode());
        assertEquals("Invalid value for client version: `1.2`. Value must be like `1.13.0`)", exception.getResponseBody());
    }
}
