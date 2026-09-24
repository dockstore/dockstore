package io.dockstore.webservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.dockstore.webservice.api.Config;
import org.junit.jupiter.api.Test;

class ConfigTest {

    @Test
    void testVersionsCopiedFromUiConfig() throws Exception {
        DockstoreWebserviceConfiguration.UIConfig uiConfig = new DockstoreWebserviceConfiguration.UIConfig();
        uiConfig.setDeployVersion("1.2.3");
        uiConfig.setSupportVersion("4.5.6");
        uiConfig.setMcpVersion("7.8.9");
        DockstoreWebserviceConfiguration webConfig = mock(DockstoreWebserviceConfiguration.class);
        when(webConfig.getUiConfig()).thenReturn(uiConfig);

        Config config = Config.fromWebConfig(webConfig);
        assertEquals("1.2.3", config.getDeployVersion());
        assertEquals("4.5.6", config.getSupportVersion());
        assertEquals("7.8.9", config.getMcpVersion());
    }
}
