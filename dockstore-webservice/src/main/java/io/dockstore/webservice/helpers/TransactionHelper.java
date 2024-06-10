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
 * <p>TransactionHelper's transaction() methods begin a transaction,
 * execute some user-specified code (in the form of a Runnable or Supplier),
 * and either commit or roll back the transaction, depending upon whether
 * the user-specified code successfully ran to completion, or threw.
 *
 * <p>When transaction() is called, any in progress-session is committed.
 * Then, the Session is cleared of entities, unless the continueSession()
 * method was called directly beforehand, in which case the Session is not
 * cleared.  After that, the user-specified code is executed in a new
 * transaction.
 *
 * If an exception is thrown from low-level Hibernate's Transaction/Session
 * manipulation code, the Session is closed (per Hibernate recommendation)
 * and the exception is wrapped in a TransactionHelperException and
 * rethrown.  At that point, surrounding code should clean up and exit,
 * because it will no longer be able to read or write to the database.
 *
 * <p> For more information on Hibernate transactions, see the
 * <a href="https://docs.jboss.org/hibernate/orm/5.6/userguide/html_single/Hibernate_User_Guide.html#transactions">Hibernate User Guide</a>
 */
public final class TransactionHelper {

    private static final Logger LOG = LoggerFactory.getLogger(TransactionHelper.class);
    private final Session session;
    private boolean continueSession;

    public TransactionHelper(Session session) {
        this.session = session;
    }

    public TransactionHelper(SessionFactory factory) {
        this(factory.getCurrentSession());
    }

    /**
     * Begin a transaction, execute the specified Runnable, and either commit
     * the database transaction when the Runnable completes, or roll back the
     * database transaction if the Runnable throws.
     */
    public void transaction(Runnable runnable) {
        transaction(() -> {
            runnable.run();
            return true;
        });
    }

    /**
     * Begin a transaction, get a value from the specified Supplier, and
     * either commit the database transaction when the Supplier returns, or
     * roll back the database transaction if the Supplier throws.  The value
     * produced by the Supplier is returned.
     */
    public <T> T transaction(Supplier<T> supplier) {
        commit();
        if (!continueSession) {
            clear();
            continueSession = false;
        }
        begin();
        boolean success = false;
        try {
            T result = supplier.get();
            success = true;
            return result;
        } finally {
            if (success) {
                commit();
            } else {
                rollback();
            }
            begin();
        }
    }

    /**
     * Instruct TransactionHelper to not clear the Session before beginning
     * the next transaction.
     */
    public TransactionHelper continueSession() {
        continueSession = true;
        return this;
    }

    private void clear() {
        try {
            session.clear();
        } catch (RuntimeException ex) {
            handle("clear session", ex);
        }
    }

    private void begin() {
        try {
            session.beginTransaction();
        } catch (RuntimeException ex) {
            handle("begin transaction", ex);
        }
    }

    private void rollback() {
        try {
            Transaction transaction = session.getTransaction();
            if (isActive(transaction) && transaction.getStatus().canRollback()) {
                transaction.rollback();
            }
        } catch (RuntimeException ex) {
            handle("rollback transaction", ex);
        }
    }

    private void commit() {
        try {
            Transaction transaction = session.getTransaction();
            if (isActive(transaction)) {
                transaction.commit();
            }
        } catch (RuntimeException ex) {
            handle("commit transaction", ex);
        }
    }

    private boolean isActive(Transaction transaction) {
        return transaction != null && transaction.isActive();
    }

    private void handle(String operation, RuntimeException ex) {
        String message = String.format("database '%s' failed", operation);
        LOG.error(message, ex);
        // To prevent us from interacting with foobared state, the
        // Hibernate docs instruct us to immediately close a session
        // if a previous operation on the session has thrown.
        try {
            session.close();
        } catch (RuntimeException closeEx) {
            LOG.error("post-Exception close failed", closeEx);
        }
        throw new TransactionHelperException(message, ex);
    }

    /**
     * Wraps session/database exceptions thrown by internal
     * TransactionHelper code, so they can be differentiated from
     * exceptions thrown by the user-specified code that's run by
     * transaction().
     */
    public static class TransactionHelperException extends RuntimeException {
        public TransactionHelperException(String message, Exception ex) {
            super(message, ex);
        }
    }
}
