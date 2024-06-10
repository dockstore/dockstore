/*
 * Copyright 2022 OICR and UCSC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.dockstore.webservice.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import io.dockstore.client.cli.BaseIT;
import io.dockstore.client.cli.BaseIT.TestStatus;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.common.MuteForSuccessfulTests;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import io.dockstore.webservice.core.Event;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.context.internal.ManagedSessionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;

/**
 * Test the TransactionHelper class.
 */
@ExtendWith(SystemStubsExtension.class)
@ExtendWith(MuteForSuccessfulTests.class)
@ExtendWith(TestStatus.class)
@Tag(ConfidentialTest.NAME)
class TransactionHelperIT extends BaseIT {

    @SystemStub
    public final SystemOut systemOut = new SystemOut();
    @SystemStub
    public final SystemErr systemErr = new SystemErr();

    private Session session;

    @BeforeEach
    public void setup() {
        DockstoreWebserviceApplication application = SUPPORT.getApplication();
        SessionFactory sessionFactory = application.getHibernate().getSessionFactory();
        session = sessionFactory.openSession();
        ManagedSessionContext.bind(session);
    }

    @AfterEach
    public void close() {
        session.close();
    }

    private void insert() {
        session.createQuery("insert into Label (id, value) values (1234L, 'foo')").executeUpdate();
    }

    private int count() {
        return session.createQuery("select count(*) from Label", Long.class).getSingleResult().intValue();
    }

    private void shouldThrow(Runnable runnable) {
        try {
            runnable.run();
            fail("should have thrown");
        } catch (Exception e) {
            // expected path of execution
        }
    }

    @Test
    void testAutoCommit() {
        TransactionHelper helper = new TransactionHelper(session);
        helper.transaction(this::insert);
        assertEquals(1, count());
    }

    @Test
    void testAutoRollback() {
        TransactionHelper helper = new TransactionHelper(session);
        shouldThrow(() -> helper.transaction(() -> {
            insert();
            throw new RuntimeException("foo");
        }));
        assertEquals(0, count());
    }

    @Test
    void testReturn() {
        TransactionHelper helper = new TransactionHelper(session);
        assertEquals(1, helper.transaction(() -> 1));
    }

    @Test
    void testContinueSession() {
        TransactionHelper helper = new TransactionHelper(session);
        Object a = helper.transaction(this::createEntity);
        assertEquals(1, sessionEntityCount());
        assertTrue(sessionContains(a));
        Object b = helper.continueSession().transaction(this::createEntity);
        assertEquals(2, sessionEntityCount());
        assertTrue(sessionContains(a));
        assertTrue(sessionContains(b));
        Object c = helper.transaction(this::createEntity);
        assertEquals(1, sessionEntityCount());
        assertFalse(sessionContains(a));
        assertFalse(sessionContains(b));
        assertTrue(sessionContains(c));
    }

    private Object createEntity() {
        Event event = new Event();
        session.save(event);
        return event;
    }

    private int sessionEntityCount() {
        return session.getStatistics().getEntityCount();
    }

    private boolean sessionContains(Object obj) {
        return session.contains(obj);
    }
}
