package io.dockstore.webservice.helpers;

import java.util.function.Predicate;
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
    private final SessionFactory factory;
    private Session session;
    private RuntimeException thrown;

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
        transaction(() -> {
            runnable.run();
            return true;
        }, x -> true);
    }

    public <T> T transaction(Supplier<T> supplier) {
        return transaction(supplier, x -> true);
    }

    public <T> T transaction(Supplier<T> supplier, Predicate<T> isSuccess) {
        try {
            thrown = null;
            session = current();
            commit();
            clear();
            begin();
            boolean success = false;
            try {
                T result = supplier.get();
                success = isSuccess.test(result);
                return result;
            } finally {
                if (thrown != null) {
                    if (success) {
                        commit();
                    } else {
                        rollback();
                    }
                }
            }
        } finally {
            session = null;
        }
    }

    private Session current() {
        try {
            return factory.getCurrentSession();
        } catch (RuntimeException ex) {
            throw handle("current", ex);
        }
    }

    public void clear() {
        check();
        try {
            session.clear();
        } catch (RuntimeException ex) {
            throw handle("clear", ex);
        }
    }

    public void begin() {
        check();
        try {
            session.beginTransaction();
        } catch (RuntimeException ex) {
            throw handle("begin", ex);
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
            throw handle("rollback", ex);
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
            throw handle("commit", ex);
        }
    }


    private void check() {
        if (session == null) {
            throw new RuntimeException("operation attempted outside of TransactionHelper.transaction()");
        }
        if (thrown != null) {
            String message = "operation on session that has thrown";
            LOG.error("operation on session that has thrown", thrown);
            throw new TransactionHelperException("operation on session that has thrown", thrown);
        }
    }

    private boolean isActive(Transaction transaction) {
        return transaction != null && transaction.isActive();
    }

    private RuntimeException handle(String operation, RuntimeException ex) {
        thrown = ex;
        String message = String.format("operation {} failed", operation);
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
