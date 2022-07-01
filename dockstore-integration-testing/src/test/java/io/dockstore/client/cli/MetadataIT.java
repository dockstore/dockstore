package io.dockstore.client.cli;

import io.dockstore.common.PipHelper;
import io.dockstore.webservice.resources.MetadataResource;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.ApiResponse;
import io.swagger.client.Pair;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.experimental.categories.Category;

@Category(OpenApiIT.class)
public class MetadataIT extends BaseIT {
    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();
    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();
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

    @Category(MetadataResource.class)
    @Test
    public void testValidClientVersion() {
        String endpoint = "/metadata/runner_dependencies";
        List<Pair> queryParams = this.queryParams();
        queryParams.addAll(apiClient.parameterToPairs("", "client_version", "1.13.0"));
        ApiResponse<Object> response = this.get_request(endpoint, queryParams);
        Assert.assertEquals(HttpStatus.OK_200, response.getStatusCode());
    }

    @Category(MetadataResource.class)
    @Test
    public void testPrereleaseClientVersion() {
        String endpoint = "/metadata/runner_dependencies";
        List<Pair> queryParams = this.queryParams();
        queryParams.addAll(apiClient.parameterToPairs("", "client_version", "1.13.0-alpha.7"));
        ApiResponse<Object> response = this.get_request(endpoint, queryParams);
        Assert.assertEquals(HttpStatus.OK_200, response.getStatusCode());
    }

    @Category(MetadataResource.class)
    @Test
    public void testDevelopmentSemanticVersion() {
        String endpoint = "/metadata/runner_dependencies";
        List<Pair> queryParams = this.queryParams();
        queryParams.addAll(apiClient.parameterToPairs("", "client_version", PipHelper.DEV_SEM_VER));
        ApiResponse<Object> response = this.get_request(endpoint, queryParams);
        Assert.assertEquals(HttpStatus.OK_200, response.getStatusCode());
    }

    @Category(MetadataResource.class)
    @Test
    public void testInvalidClientVersion() {
        String endpoint = "/metadata/runner_dependencies";
        List<Pair> queryParams = this.queryParams();
        queryParams.addAll(apiClient.parameterToPairs("", "client_version", "1.2"));
        ApiException exception = Assert.assertThrows(ApiException.class, () -> this.get_request(endpoint, queryParams));
        Assert.assertEquals(HttpStatus.BAD_REQUEST_400, exception.getCode());
        Assert.assertEquals("Invalid value for client version: `1.2`. Value must be like `1.13.0`)",
                            exception.getResponseBody());
    }
}
