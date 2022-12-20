package io.dockstore.webservice.helpers;

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
 * <p> If any of the primitives throw, the exception is saved and
 * accessible via the thrown() method.  Any subsequent call to a
 * primitive will immediately throw an exception.
 *
 * <p> For more information on Hibernate transactions, see:
 * https://docs.jboss.org/hibernate/orm/5.6/userguide/html_single/Hibernate_User_Guide.html#transactions
 */
public final class TransactionHelper {

    private static final Logger LOG = LoggerFactory.getLogger(TransactionHelper.class);
    private Session session;
    private Throwable thrown;

    public TransactionHelper(Session session) {
        this.session = session;
        this.thrown = null;
    }

    public TransactionHelper(SessionFactory factory) {
        this(factory.getCurrentSession());
    }

    /**
     * Begin a transaction, execute the specified runnable, and either commit
     * the database transaction when the runnable returns, or roll back the
     * database transaction if the runnable throws.  Prior to the transaction,
     * any previous transaction is committed and the session is cleared.
     * To rollback the previous transaction, call rollback() before calling
     * transaction().
     */
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
        } catch (RuntimeException | Error throwable) {
            handle("clear", throwable);
        }
    }

    public void begin() {
        check();
        try {
            session.beginTransaction();
        } catch (RuntimeException | Error throwable) {
            handle("begin", throwable);
        }
    }

    public void rollback() {
        check();
        try {
            Transaction transaction = session.getTransaction();
            if (isActive(transaction) && transaction.getStatus().canRollback()) {
                transaction.rollback();
            }
        } catch (RuntimeException | Error throwable) {
            handle("rollback", throwable);
        }
    }

    public void commit() {
        check();
        try {
            Transaction transaction = session.getTransaction();
            if (isActive(transaction)) {
                transaction.commit();
            }
        } catch (RuntimeException | Error throwable) {
            handle("commit", throwable);
        }
    }

    private boolean isActive(Transaction transaction) {
        return transaction != null && transaction.isActive();
    }

    public Throwable thrown() {
        return thrown;
    }

    private void check() {
        if (thrown != null) {
            LOG.error("operation on session that has previously thrown", thrown);
            thrown = new Error("operation on session that has previously thrown");
            rethrow(thrown);
        }
    }

    private void handle(String operation, Throwable throwable) {
        thrown = throwable;
        LOG.error("{} failed", operation, throwable);
        // To prevent us from interacting with foobared state, the
        // Hibernate docs instruct us to immediately close a session
        // if a previous operation on the session has thrown.
        try {
            session.close();
        } catch (Throwable closeThrowable) {
            LOG.error("post-throw close failed", closeThrowable);
        }
        rethrow(throwable);
    }

    private void rethrow(Throwable throwable) {
        if (throwable instanceof RuntimeException) {
            throw (RuntimeException)throwable;
        } else if (throwable instanceof Error) {
            throw (Error)throwable;
        } else {
            LOG.error("attempt to rethrow unexpected checked exception", throwable);
            throw new RuntimeException(throwable);
        }
    }
}
