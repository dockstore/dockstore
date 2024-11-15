package io.dockstore.webservice.helpers;

import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.persistence.PersistenceException;
import org.hibernate.TransactionException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;

class ExceptionHelperTest {

    @Test
    void testExceptionLineage() {
        // In https://ucsc-cgl.atlassian.net/browse/DOCK-2582,
        // we deleted TransactionExceptionMapper and ConstraintExceptionMapper
        // because TransactionException and ConstraintViolationException
        // are both subclasses of PersistenceException, and PersistenceException
        // is handled by the existing PersistenceExceptionMapper.
        // Confirm the subclass relationship:
        assertTrue(PersistenceException.class.isAssignableFrom(TransactionException.class));
        assertTrue(PersistenceException.class.isAssignableFrom(ConstraintViolationException.class));
    }
}
