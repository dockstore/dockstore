package io.dockstore.client.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.dockstore.client.cli.BaseIT.TestStatus;
import io.dockstore.common.PipHelper;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.ApiResponse;
import io.swagger.client.Pair;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;
import uk.org.webcompere.systemstubs.stream.output.NoopStream;

@ExtendWith(SystemStubsExtension.class)
@ExtendWith(TestStatus.class)
public class MetadataIT extends BaseIT {
    @SystemStub
    public final SystemOut systemOutRule = new SystemOut(new NoopStream());
    @SystemStub
    public final SystemErr systemErrRule = new SystemErr(new NoopStream());

    public ApiClient apiClient = getWebClient();

    public ApiResponse<Object> get_request(String endpoint, List<Pair> queryParams) {
        return apiClient.invokeAPI(endpoint, "GET", queryParams, null, new HashMap<>(), new HashMap<>(), "application/json",
                "application/json", new String[] { "BEARER" }, null);

    }

    public List<Pair> queryParams() {
        List<Pair> queryParams = new ArrayList<>();
        queryParams.addAll(apiClient.parameterToPairs("", "python_version", "3"));
        queryParams.addAll(apiClient.parameterToPairs("", "runner", "cwltool"));
        queryParams.addAll(apiClient.parameterToPairs("", "output", "json"));
        return queryParams;
    }

    @Test
    public void testValidClientVersion() {
        String endpoint = "/metadata/runner_dependencies";
        List<Pair> queryParams = this.queryParams();
        queryParams.addAll(apiClient.parameterToPairs("", "client_version", "1.13.0"));
        ApiResponse<Object> response = this.get_request(endpoint, queryParams);
        assertEquals(HttpStatus.OK_200, response.getStatusCode());
    }

    @Test
    public void testPrereleaseClientVersion() {
        String endpoint = "/metadata/runner_dependencies";
        List<Pair> queryParams = this.queryParams();
        queryParams.addAll(apiClient.parameterToPairs("", "client_version", "1.13.0-alpha.7"));
        ApiResponse<Object> response = this.get_request(endpoint, queryParams);
        assertEquals(HttpStatus.OK_200, response.getStatusCode());
    }

    @Test
    public void testDevelopmentSemanticVersion() {
        String endpoint = "/metadata/runner_dependencies";
        List<Pair> queryParams = this.queryParams();
        queryParams.addAll(apiClient.parameterToPairs("", "client_version", PipHelper.DEV_SEM_VER));
        ApiResponse<Object> response = this.get_request(endpoint, queryParams);
        assertEquals(HttpStatus.OK_200, response.getStatusCode());
    }

    @Test
    public void testInvalidClientVersion() {
        String endpoint = "/metadata/runner_dependencies";
        List<Pair> queryParams = this.queryParams();
        queryParams.addAll(apiClient.parameterToPairs("", "client_version", "1.2"));
        ApiException exception = Assert.assertThrows(ApiException.class, () -> this.get_request(endpoint, queryParams));
        assertEquals(HttpStatus.BAD_REQUEST_400, exception.getCode());
        assertEquals("Invalid value for client version: `1.2`. Value must be like `1.13.0`)", exception.getResponseBody());
    }
}
