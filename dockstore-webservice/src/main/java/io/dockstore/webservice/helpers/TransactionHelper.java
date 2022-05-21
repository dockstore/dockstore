package io.dockstore.webservice.helpers;

import org.hibernate.SessionFactory;
import org.hibernate.Transaction;

public final class TransactionHelper {

    private TransactionHelper() {
    }

    public static void begin(SessionFactory sessionFactory) {
        sessionFactory.getCurrentSession().beginTransaction();
    }

    public static void rollback(SessionFactory sessionFactory) {
        Transaction transaction = currentTransaction(sessionFactory);
        if (isActive(transaction) && transaction.getStatus().canRollback()) {
            transaction.rollback();
        }
    }

    public static void commit(SessionFactory sessionFactory) {
        Transaction transaction = currentTransaction(sessionFactory);
        if (isActive(transaction)) {
            transaction.commit();
        }
    }

    public static void rollbackThenBegin(SessionFactory sessionFactory) {
        rollback(sessionFactory);
        begin(sessionFactory);
    }

    public static void commitThenBegin(SessionFactory sessionFactory) {
        commit(sessionFactory);
        begin(sessionFactory);
    }

    private static Transaction currentTransaction(SessionFactory sessionFactory) {
        return sessionFactory.getCurrentSession().getTransaction();
    }

    private static boolean isActive(Transaction transaction) {
        return transaction != null && transaction.isActive();
    }
}
