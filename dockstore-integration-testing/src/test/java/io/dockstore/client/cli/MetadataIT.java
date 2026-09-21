package io.dockstore.client.cli;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.dockstore.client.cli.BaseIT.TestStatus;
import io.dockstore.common.MuteForSuccessfulTests;
import io.dockstore.common.PipHelper;
import io.dockstore.openapi.client.ApiException;
import io.dockstore.openapi.client.api.MetadataApi;
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

    public MetadataApi metadataApi = new MetadataApi(getAnonymousOpenAPIWebClient());

    @Test
    void testValidClientVersion() {
        // The openapi-generated client, unlike the swagger one, doesn't expose a "WithHttpInfo" variant that
        // returns the raw status code; a successful call not throwing is sufficient confirmation of a 200.
        assertDoesNotThrow(() -> metadataApi.getRunnerDependencies("1.13.0", "3", "cwltool", "json"));
    }

    @Test
    void testPrereleaseClientVersion() {
        assertDoesNotThrow(() -> metadataApi.getRunnerDependencies("1.13.0-alpha.7", "3", "cwltool", "json"));
    }

    @Test
    void testDevelopmentSemanticVersion() {
        assertDoesNotThrow(() -> metadataApi.getRunnerDependencies(PipHelper.DEV_SEM_VER, "3", "cwltool", "json"));
    }

    @Test
    void testInvalidClientVersion() {
        ApiException exception = assertThrows(ApiException.class, () -> metadataApi.getRunnerDependencies("1.2", "3", "cwltool", "json"));
        assertEquals(HttpStatus.BAD_REQUEST_400, exception.getCode());
        assertEquals("Invalid value for client version: `1.2`. Value must be like `1.13.0`)", exception.getResponseBody());
    }
}
