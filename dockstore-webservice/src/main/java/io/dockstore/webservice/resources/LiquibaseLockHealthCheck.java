package io.dockstore.webservice.resources;

import com.codahale.metrics.health.HealthCheck;
import java.util.Date;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.context.internal.ManagedSessionContext;
import org.hibernate.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LiquibaseLockHealthCheck extends HealthCheck  {
    private static final Logger LOG = LoggerFactory.getLogger(LiquibaseLockHealthCheck.class);
    private final SessionFactory sessionFactory;

    public LiquibaseLockHealthCheck(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    @Override
    protected Result check() throws Exception {

        Session session = sessionFactory.openSession();
        ManagedSessionContext.bind(session);
        Query query = session.createNativeQuery("select lockgranted from databasechangeloglock");
        Object result = query.getSingleResult();
        session.close();

        if (result == null) {
            LOG.info("Liquibase lock is free");
            return Result.healthy();
        }

        if (result instanceof Date grantedDate) {
            long heldSeconds = (new Date().getTime() - grantedDate.getTime()) / 1000L;
            LOG.info(String.format("Liquibase lock was granted at %s, held for %d seconds", grantedDate, heldSeconds));
            if (heldSeconds > 600L) {
                LOG.error("Liquibase lock was held too long");
                return Result.unhealthy("Liquibase lock was held too long");
            } else {
                return Result.healthy();
            }
        }

        LOG.error("Unexpected result from liquibase query"); 
        return Result.unhealthy("Unexpected result from liquibase query");
    }
}
