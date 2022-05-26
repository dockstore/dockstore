package io.dockstore.webservice.helpers;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * See the end of Section 13.2.1
 * https://docs.jboss.org/hibernate/core/4.3/manual/en-US/html/ch13.html
 */
public final class TransactionHelper {

    private static final Logger LOG = LoggerFactory.getLogger(TransactionHelper.class);

    private TransactionHelper() {
    }

    public static boolean transaction(SessionFactory sessionFactory, Runnable runnable) {
        boolean completed = false;
        clear(sessionFactory);
        begin(sessionFactory);
        try {
            runnable.run();
            completed = true;
        } catch (RollbackTransactionException e) {
            // keeps the RollbackTransactionException from propagating
        } finally {
            if (completed) {
                commit(sessionFactory);
            } else {
                rollback(sessionFactory);
            }
        }
        return completed;
    }

    public static void clear(SessionFactory sessionFactory) {
        check();
        try {
            currentSession(sessionFactory).clear();
        } catch (Exception ex) {
            handleException("clear", ex);
        }
    }

    public static void begin(SessionFactory sessionFactory) {
        check();
        try {
            currentSession(sessionFactory).beginTransaction();
        } catch (Exception ex) {
            handleException("begin", ex);
        }
    }

    public static void rollback(SessionFactory sessionFactory) {
        check();
        try {
            Transaction transaction = currentTransaction(sessionFactory);
            if (isActive(transaction) && transaction.getStatus().canRollback()) {
                transaction.rollback();
            }
        } catch (Exception ex) {
            handleException("rollback", ex);
        }
    }

    public static void commit(SessionFactory sessionFactory) {
        check();
        try {
            Transaction transaction = currentTransaction(sessionFactory);
            if (isActive(transaction)) {
                transaction.commit();
            }
        } catch (Exception ex) {
            handleException("commit", ex);
        }
    }

    public static boolean isClosed(SessionFactory sessionFactory) {
        return !currentSession(sessionFactory).isOpen();
    }

    private static void check() {
        if (isClosed(sessionFactory)) {
            LOG.error("TransactionHelper: operation on closed session");
            throw new RuntimeException("operation on closed session");
        }
    }

    private static void handleException(String operation, Exception ex) throws Exception {
        LOG.error(String.format("TransactionHelper: %s failed", operation), ex);
        // The docs instruct us to immediately close the session if it throws an error.
        // This will keep us from using foobar-ed state.
        try {
            currentSession(sessionFactory).close();
        } catch (Exception closeEx) {
            LOG.error("TransactionHelper: close after exception failed", closeEx);
        }
        throw ex;
    }

    private static Session currentSession(SessionFactory sessionFactory) {
        return sessionFactory.getCurrentSession();
    }

    private static Transaction currentTransaction(SessionFactory sessionFactory) {
        return currentSession(sessionFactory).getTransaction();
    }

    private static boolean isActive(Transaction transaction) {
        return transaction != null && transaction.isActive();
    }

    public static class RollbackTransactionException extends RuntimeException {
    }
}
