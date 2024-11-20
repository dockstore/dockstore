package io.dockstore.webservice.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.persistence.PersistenceException;
import jakarta.ws.rs.core.Response.Status;
import org.hibernate.TransactionException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

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

    @Test
    void testUnknownException() {
        String message = "this is a test";
        Throwable t = makeNonJavaException(message);
        assertEquals(t.getMessage(), message(t));
        assertEquals(Status.BAD_REQUEST, status(t));
    }

    @Test
    void testJavaThrowable() {
        Throwable t = makeNullPointerException();
        assertEquals(t.getMessage(), message(t));
        assertEquals(Status.INTERNAL_SERVER_ERROR, status(t));
    }

    @Test
    void testConstraintViolationException() {
        Throwable t = makeConstraintViolationException("xyz_123");
        assertTrue(contains(message(t), "constraint", "violated", "xyz_123"));
        assertEquals(Status.CONFLICT, status(t));

        t = wrap(t, "something went wrong");
        assertTrue(contains(message(t), "constraint", "violated", "xyz_123"));
        assertEquals(Status.CONFLICT, status(t));

        t = makeConstraintViolationException("check_valid_doi");
        assertTrue(contains(message(t), "doi", "valid"));
        assertEquals(Status.CONFLICT, status(t));

        t = makeConstraintViolationException(null);
        assertTrue(contains(message(t), "constraint", "violated"));
        assertEquals(Status.CONFLICT, status(t));
    }

    @Test
    void testPersistenceException() {
        Throwable t = makePersistenceException();
        assertTrue(contains(message(t), "database", "updated"));
        assertEquals(Status.INTERNAL_SERVER_ERROR, status(t));
    }

    @Test
    @Timeout(3)
    void testVeryLargeNumberOfCauses() {
        Throwable t = new RuntimeException();
        for (int i = 0; i < 200000; i++) {
            t = new Throwable("wrapper", t, false, false) {};
        }
        message(t);
        status(t);
    }

    private Throwable makeNonJavaException(String message) {
        return new RuntimeException(message) {};
    }

    private Throwable wrap(Throwable t, String message) {
        return new RuntimeException(message, t) {};
    }

    private NullPointerException makeNullPointerException() {
        try {
            Object o = null;
            o.hashCode();
        } catch (NullPointerException e) {
            return e;
        }
        throw new RuntimeException("should not be reachable");
    }

    private PersistenceException makePersistenceException() {
        return new PersistenceException();
    }

    private ConstraintViolationException makeConstraintViolationException(String constraintName) {
        return new ConstraintViolationException("oops", null, constraintName);
    }

    private String message(Throwable t) {
        return new ExceptionHelper(t).info().message();
    }

    private int status(Throwable t) {
        return new ExceptionHelper(t).info().status();
    }

    private boolean contains(String s, String... substrings) {
        for (String substring: substrings) {
            if (!s.toLowerCase().contains(substring.toLowerCase())) {
                return false;
            }
        }
        return true;
    }
}
