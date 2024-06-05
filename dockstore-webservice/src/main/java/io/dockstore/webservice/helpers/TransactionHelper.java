package io.dockstore.webservice.helpers;

import java.util.function.Supplier;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides a clean interface to Hibernate transactions.
 *
 * <p>The easiest way to use this class is via the transaction() method,
 * which begins a transaction, executes a runnable, and either commits
 * or rolls back the transaction, depending upon whether the runnable
 * ran to completion, or threw.
 *
 * <p> Under the hood, the transaction() method calls various methods
 * that implement various primitive transaction/session operations,
 * which you can also call directly to implement a particular behavior.
 *
 * <p>The commit() and rollback() primitives must be called on
 * a transaction that is open.  Only the first call has any effect on
 * a given transaction, the subsequent commit()s or rollbacks() on that
 * transaction are ignored.
 *
 * <p> For more information on Hibernate transactions, see:
 * <a href="https://docs.jboss.org/hibernate/orm/5.6/userguide/html_single/Hibernate_User_Guide.html#transactions">...</a>
 */
public final class TransactionHelper {

    private static final Logger LOG = LoggerFactory.getLogger(TransactionHelper.class);
    private SessionFactory factory;

    public TransactionHelper(SessionFactory factory) {
        this.factory = factory;
    }

    /**
     * Begin a transaction, execute the specified Runnable, and either commit
     * the database transaction when the Runnable returns, or roll back the
     * database transaction if the Runnable throws.  Prior to the transaction,
     * any previous transaction is committed and the session is cleared.
     * To rollback the previous transaction, call rollback() before calling
     * transaction().
     */
    public void transaction(Runnable runnable) {
        Session session = current();
        commit(session);
        clear(session);
        begin(session);
        boolean success = false;
        try {
            runnable.run();
            success = true;
        } finally {
            if (success) {
                commit(session);
            } else {
                rollback(session);
            }
        }
    }

    public <T> T transaction(Supplier<T> supplier) {
        Session session = current();
        commit(session);
        clear(session);
        begin(session);
        boolean success = false;
        try {
            T result = supplier.run();
            success = true;
            return result;
        } finally {
            if (success) {
                commit(session);
            } else {
                rollback(session);
            }
        }
    }

    private Session current() {
        try {
            return factory.getCurrentSession();
        } catch (RuntimeException ex) {
            throw handle(null, "current", ex);
        }
    }

    private void clear(Session session) {
        try {
            session.clear();
        } catch (RuntimeException ex) {
            throw handle(session, "clear", ex);
        }
    }

    private void begin(Session session) {
        try {
            session.beginTransaction();
        } catch (RuntimeException ex) {
            throw handle(session, "begin", ex);
        }
    }

    private void rollback(Session session) {
        try {
            Transaction transaction = session.getTransaction();
            if (isActive(transaction) && transaction.getStatus().canRollback()) {
                transaction.rollback();
            }
        } catch (RuntimeException ex) {
            throw handle(session, "rollback", ex);
        }
    }

    private void commit(Session session) {
        try {
            Transaction transaction = session.getTransaction();
            if (isActive(transaction)) {
                transaction.commit();
            }
        } catch (RuntimeException ex) {
            throw handle(session, "commit", ex);
        }
    }

    private boolean isActive(Transaction transaction) {
        return transaction != null && transaction.isActive();
    }

    private RuntimeException handle(Session session, String operation, RuntimeException ex) {
        String message = String.format("TransactionHelper {} failed", operation);
        LOG.error(message, ex);
        // To prevent us from interacting with foobared state, the
        // Hibernate docs instruct us to immediately close a session
        // if a previous operation on the session has thrown.
        try {
            if (session != null) { 
                session.close();
            }
        } catch (RuntimeException closeEx) {
            LOG.error("post-Exception close failed", closeEx);
        }
        return new TransactionHelperException(message, ex);
    }

    public static class TransactionHelperException extends RuntimeException {
        public TransactionHelperException(String message, Exception ex) {
            super(message, ex);
        }
    }
}
