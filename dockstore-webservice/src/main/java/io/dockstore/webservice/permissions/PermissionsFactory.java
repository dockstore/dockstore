package io.dockstore.webservice.permissions;

import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.jdbi.TokenDAO;
import io.dockstore.webservice.permissions.sam.SamPermissionsImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PermissionsFactory {

    private static PermissionsInterface permissionsInterface;

    private static final Logger LOG = LoggerFactory.getLogger(PermissionsFactory.class);

    private PermissionsFactory() {
    }

    public static PermissionsInterface getAuthorizer(TokenDAO tokenDAO, DockstoreWebserviceConfiguration configuration) {
        synchronized (PermissionsFactory.class) {
            if (permissionsInterface == null) {
                String authorizerType = configuration.getAuthorizerType();
                if ("sam".equalsIgnoreCase(authorizerType)) {
                    permissionsInterface = new SamPermissionsImpl(tokenDAO, configuration);
                    LOG.info("Using SAM for sharing");
                } else if ("inmemory".equalsIgnoreCase(authorizerType)) {
                    permissionsInterface = new InMemoryPermissionsImpl();
                    LOG.info("Using InMemoryPermissionsImpl for sharing");
                } else {
                    permissionsInterface = new NoOpPermissionsImpl();
                    LOG.info("Using NoOpPermissionsImpl for sharing");
                }
            }
            return permissionsInterface;
        }
    }
}
