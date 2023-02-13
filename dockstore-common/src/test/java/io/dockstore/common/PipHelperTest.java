package io.dockstore.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * @author gluu
 * @since 14/08/18
 */
class PipHelperTest {
    @Test
    void convertSemVerToAvailableVersion() {
        assertEquals("1.14.0", PipHelper.convertSemVerToAvailableVersion(PipHelper.DEV_SEM_VER));
        assertEquals("1.14.0", PipHelper.convertSemVerToAvailableVersion("1.14.1"));
        assertEquals("1.14.0", PipHelper.convertSemVerToAvailableVersion("1.14.1-snapshot"));
        assertEquals("1.14.0", PipHelper.convertSemVerToAvailableVersion("1.14.0"));
        assertEquals("1.14.0", PipHelper.convertSemVerToAvailableVersion(null));
        assertEquals("1.14.0", PipHelper.convertSemVerToAvailableVersion("9000.9000.9000"));
        assertEquals("1.13.0", PipHelper.convertSemVerToAvailableVersion("1.13.1"));
        assertEquals("1.13.0", PipHelper.convertSemVerToAvailableVersion("1.13.1-snapshot"));
        assertEquals("1.13.0", PipHelper.convertSemVerToAvailableVersion("1.13.0"));
        assertEquals("1.13.0", PipHelper.convertSemVerToAvailableVersion("1.13.0-snapshot"));
    }
}
