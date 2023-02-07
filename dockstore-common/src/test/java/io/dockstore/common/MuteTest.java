package io.dockstore.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;

@ExtendWith(SystemStubsExtension.class)
@ExtendWith(MuteForSuccessfulTests.class)
class MuteTest {

    @SystemStub
    SystemOut systemOut = new SystemOut();

    @SystemStub
    SystemErr systemErr;

    private int a;

    @Test
    void testSuccess() {
        System.out.println("some success output to stdout");
        System.err.println("some success output to stderr");
    }

    @Test
    void testFail() {
        System.out.println("some fail output to stdout");
        System.err.println("some fail output to stderr");
        throw new RuntimeException();
    }

    @Test
    void testSuccessAgain() {
        System.out.println("more success output to stdout");
        System.err.println("more success output to stderr");
    }
}
