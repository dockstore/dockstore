package io.dockstore.webservice.jdbi;

import io.dockstore.webservice.core.CloudInstance;
import io.dropwizard.hibernate.AbstractDAO;
import org.hibernate.SessionFactory;

public class CloudInstanceDAO extends AbstractDAO<CloudInstance> {
    public CloudInstanceDAO(SessionFactory sessionFactory) {
        super(sessionFactory);
    }
}
