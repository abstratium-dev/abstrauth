package dev.abstratium.abstrauth.util;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.UserTransaction;

/**
 * Utility helper for managing transactions in tests.
 * Provides safe begin/commit/rollback operations that handle leaked transactions.
 */
@ApplicationScoped
public class TestTransactionHelper {

    @Inject
    UserTransaction userTransaction;

    public void beginTransaction() throws Exception {
        int status = userTransaction.getStatus();
        if (status != jakarta.transaction.Status.STATUS_NO_TRANSACTION) {
            userTransaction.rollback();
        }
        userTransaction.begin();
    }

    public void commitTransaction() throws Exception {
        int status = userTransaction.getStatus();
        if (status == jakarta.transaction.Status.STATUS_ACTIVE) {
            userTransaction.commit();
        } else if (status != jakarta.transaction.Status.STATUS_NO_TRANSACTION) {
            userTransaction.rollback();
        }
    }

    public void rollback() throws Exception {
        int status = userTransaction.getStatus();
        if (status != jakarta.transaction.Status.STATUS_NO_TRANSACTION) {
            userTransaction.rollback();
        }
    }

    public int getStatus() throws Exception {
        return userTransaction.getStatus();
    }
}
