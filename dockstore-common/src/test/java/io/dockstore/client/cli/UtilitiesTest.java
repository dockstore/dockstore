package io.dockstore.client.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.io.ByteStreams;
import io.dockstore.common.MuteForSuccessfulTests;
import io.dockstore.common.Utilities;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;

@ExtendWith(SystemStubsExtension.class)
@ExtendWith(MuteForSuccessfulTests.class)
class UtilitiesTest {

    @SystemStub
    public final SystemOut systemOut = new SystemOut();

    @SystemStub
    public final SystemErr systemErr = new SystemErr();

    @Test
    void testEnvironmentParam() {
        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        final Map<String, String> map = new HashMap<>();
        map.put("foo", "goo");

        // Ensure foo gets substituted with goo
        Utilities.executeCommand("echo ${foo}", os, ByteStreams.nullOutputStream(), new File("."), map);
        assertEquals("goo\n", os.toString());

        // Make sure that a non-existent variable works
        os.reset();
        Utilities.executeCommand("echo ${foo}", os, ByteStreams.nullOutputStream(), new File("."), null);
        assertEquals("${foo}\n", os.toString());
    }
}
