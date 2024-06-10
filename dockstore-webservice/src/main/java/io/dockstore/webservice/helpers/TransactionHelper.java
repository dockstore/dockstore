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
 * <p> For more information, see the
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
     * the database transaction when the Runnable returns, or roll back the
     * database transaction if the Runnable throws.  Prior to the transaction,
     * any previous transaction is committed and the session is cleared.
     * To rollback the previous transaction, call rollback() before calling
     * transaction().
     */
    public void transaction(Runnable runnable) {
        transaction(() -> {
            runnable.run();
            return true;
        });
    }

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
