package io.dockstore.webservice.permissions;

import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dockstore.webservice.jdbi.TokenDAO;
import io.dockstore.webservice.permissions.sam.SamPermissionsImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PermissionsFactory {

    private static final Logger LOG = LoggerFactory.getLogger(PermissionsFactory.class);

    private PermissionsFactory() {
    }

    public static PermissionsInterface createAuthorizer(TokenDAO tokenDAO, DockstoreWebserviceConfiguration configuration) {
        String authorizerType = configuration.getAuthorizerType();
        if ("sam".equalsIgnoreCase(authorizerType)) {
            LOG.info("Using SAM for sharing");
            return new SamPermissionsImpl(tokenDAO, configuration);
        } else if ("inmemory".equalsIgnoreCase(authorizerType)) {
            LOG.info("Using InMemoryPermissionsImpl for sharing");
            return new InMemoryPermissionsImpl();
        } else {
            LOG.info("Using NoOpPermissionsImpl for sharing");
            return new NoOpPermissionsImpl();
        }
    }
}
