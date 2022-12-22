package io.dockstore.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * @author gluu
 * @since 14/08/18
 */
public class PipHelperTest {
    @Test
    public void convertSemVerToAvailableVersion() {
        assertEquals("1.13.0", PipHelper.convertSemVerToAvailableVersion(PipHelper.DEV_SEM_VER));
        assertEquals("1.13.0", PipHelper.convertSemVerToAvailableVersion(null));
        assertEquals("1.13.0", PipHelper.convertSemVerToAvailableVersion("9000.9000.9000"));
        assertEquals("1.13.0", PipHelper.convertSemVerToAvailableVersion("1.13.1"));
        assertEquals("1.13.0", PipHelper.convertSemVerToAvailableVersion("1.13.1-snapshot"));
        assertEquals("1.13.0", PipHelper.convertSemVerToAvailableVersion("1.13.0"));
        assertEquals("1.10.0", PipHelper.convertSemVerToAvailableVersion("1.13.0-snapshot"));
        assertEquals("1.10.0", PipHelper.convertSemVerToAvailableVersion("1.12.0-snapshot"));
        assertEquals("1.10.0", PipHelper.convertSemVerToAvailableVersion("1.12.1"));
        assertEquals("1.10.0", PipHelper.convertSemVerToAvailableVersion("1.12.1-snapshot"));
        assertEquals("1.10.0", PipHelper.convertSemVerToAvailableVersion("1.12.0"));
        assertEquals("1.10.0", PipHelper.convertSemVerToAvailableVersion("1.12.0-snapshot"));
        assertEquals("1.10.0", PipHelper.convertSemVerToAvailableVersion("1.11.1"));
        assertEquals("1.10.0", PipHelper.convertSemVerToAvailableVersion("1.11.0"));
        assertEquals("1.10.0", PipHelper.convertSemVerToAvailableVersion("1.10.0-snapshot"));
        assertEquals("1.10.0", PipHelper.convertSemVerToAvailableVersion("1.10.1"));
        assertEquals("1.10.0", PipHelper.convertSemVerToAvailableVersion("1.10.0"));
    }
}
