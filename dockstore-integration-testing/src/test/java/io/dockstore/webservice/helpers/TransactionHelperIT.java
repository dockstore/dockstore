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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

import io.dockstore.client.cli.BaseIT;
import io.dockstore.client.cli.BaseIT.TestStatus;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.context.internal.ManagedSessionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;
import uk.org.webcompere.systemstubs.stream.output.NoopStream;

/**
 * Test the TransactionHelper class.
 */
@ExtendWith(SystemStubsExtension.class)
@ExtendWith(TestStatus.class)
@Tag(ConfidentialTest.NAME)
public class TransactionHelperIT extends BaseIT {

    @SystemStub
    public final SystemOut systemOutRule = new SystemOut(new NoopStream());
    @SystemStub
    public final SystemErr systemErrRule = new SystemErr(new NoopStream());

    private Session session;

    @BeforeEach
    public void setup() {
        DockstoreWebserviceApplication application = SUPPORT.getApplication();
        SessionFactory sessionFactory = application.getHibernate().getSessionFactory();
        session = sessionFactory.openSession();
        ManagedSessionContext.bind(session);
    }

    private void insert() {
        session.createSQLQuery("insert into Label values (1234, 'foo')").executeUpdate();
    }

    private int count() {
        return session.createSQLQuery("select * from Label").list().size();
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
    public void testTransactionAutoCommit() {
        TransactionHelper helper = new TransactionHelper(session);
        helper.transaction(this::insert);
        helper.transaction(() -> assertEquals(1, count()));
    }

    @Test
    public void testTransactionAutoRollback() {
        TransactionHelper helper = new TransactionHelper(session);
        shouldThrow(() -> helper.transaction(() -> {
            insert();
            throw new RuntimeException("foo");
        }));
        helper.transaction(() -> assertEquals(0, count()));
        assertNull(helper.thrown());
    }

    @Test
    public void testRepeatedCommitsAndRollbacks() {
        TransactionHelper helper = new TransactionHelper(session);
        helper.commit();
        helper.rollback();
        helper.transaction(this::insert);
        helper.rollback();
        helper.rollback();
        helper.commit();
        helper.commit();
        shouldThrow(() -> helper.transaction(this::insert));
        assertNull(helper.thrown());
        helper.commit();
        helper.commit();
        helper.rollback();
        helper.rollback();
        helper.transaction(() -> assertEquals(1, count()));
    }

    @Test
    public void testThrowsOnClosedSession() {
        TransactionHelper helper = new TransactionHelper(session);
        session.close();
        shouldThrow(helper::begin);
        assertNotNull(helper.thrown());
        shouldThrow(() -> helper.transaction(this::insert));
        assertNotNull(helper.thrown());
    }
}
