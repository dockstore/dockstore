package io.dockstore.webservice.helpers;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * See https://docs.jboss.org/hibernate/orm/5.6/userguide/html_single/Hibernate_User_Guide.html#transactions
 */
public final class TransactionHelper {

    private static final Logger LOG = LoggerFactory.getLogger(TransactionHelper.class);
    private Session session;
    private RuntimeException thrown;

    public TransactionHelper(Session session) {
        this.session = session;
        this.thrown = null;
    }

    public TransactionHelper(SessionFactory factory) {
        this(factory.getCurrentSession());
    }

    public void transaction(Runnable runnable) {
        boolean success = false;
        commit();
        clear();
        begin();
        try {
            runnable.run();
            success = true;
        } finally {
            if (success) {
                commit();
            } else {
                rollback();
            }
        }
    }

    public void clear() {
        check();
        try {
            session.clear();
        } catch (RuntimeException ex) {
            handle("clear", ex);
        }
    }

    public void begin() {
        check();
        try {
            session.beginTransaction();
        } catch (RuntimeException ex) {
            handle("begin", ex);
        }
    }

    public void rollback() {
        check();
        try {
            Transaction transaction = session.getTransaction();
            if (isActive(transaction) && transaction.getStatus().canRollback()) {
                transaction.rollback();
            }
        } catch (RuntimeException ex) {
            handle("rollback", ex);
        }
    }

    public void commit() {
        check();
        try {
            Transaction transaction = session.getTransaction();
            if (isActive(transaction)) {
                transaction.commit();
            }
        } catch (RuntimeException ex) {
            handle("commit", ex);
        }
    }

    public RuntimeException thrown() {
        return thrown;
    }

    private void check() {
        if (thrown != null) {
            LOG.error("operation on session that has thrown", thrown);
            thrown = new RuntimeException("operation on session that has thrown");
            throw thrown;
        }
    }

    private void handle(String operation, RuntimeException ex) {
        thrown = ex;
        LOG.error(operation + " failed", ex);
        // To prevent us from interacting with foobared state, the
        // Hibernate docs instruct us to immediately close a session
        // if an operation on the session has thrown.
        try {
            session.close();
        } catch (RuntimeException closeEx) {
            LOG.error("post-Exception close failed", closeEx);
        }
        throw ex;
    }

    private boolean isActive(Transaction transaction) {
        return transaction != null && transaction.isActive();
    }
}
