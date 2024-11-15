package io.dockstore.webservice.helpers;

import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.persistence.PersistenceException;
import org.hibernate.TransactionException;
import org.junit.jupiter.api.Test;

class ExceptionHelperTest {

    @Test
    void testExceptionLineage() {
        // In https://ucsc-cgl.atlassian.net/browse/DOCK-2582,
        // we deleted TransactionExceptionMapper, because TransactionException
        // is a subclass of PersistenceException, and a PersistenceException
        // is handled by PersistenceExceptionMapper.
        // Confirm that TransactionException subclasses PersistenceException:
        assertTrue(PersistenceException.class.isAssignableFrom(TransactionException.class));
    }
}
