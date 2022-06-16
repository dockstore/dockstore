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

import io.dockstore.client.cli.BaseIT;
import io.dockstore.common.ConfidentialTest;
import io.dockstore.webservice.DockstoreWebserviceApplication;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.context.internal.ManagedSessionContext;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;

@Category(ConfidentialTest.class)
public class TransactionHelperIT extends BaseIT {

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final ExpectedSystemExit systemExit = ExpectedSystemExit.none();

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private Session session;

    @Before
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
            Assert.fail("should have thrown");
        } catch (Exception e) {
            // expected path of execution
        }
    }

    @Test
    public void testTransactionAutoCommit() {
        TransactionHelper helper = new TransactionHelper(session);
        helper.transaction(() -> insert());
        helper.transaction(() -> Assert.assertEquals(1, count()));
    }

    @Test
    public void testTransactionAutoRollback() {
        TransactionHelper helper = new TransactionHelper(session);
        shouldThrow(() -> helper.transaction(() -> {
            insert();
            throw new RuntimeException("foo");
        }));
        helper.transaction(() -> Assert.assertEquals(0, count()));
        Assert.assertNull(helper.thrown());
    }

    @Test
    public void testRepeatedCommitsAndRollbacks() {
        TransactionHelper helper = new TransactionHelper(session);
        helper.commit();
        helper.rollback();
        helper.transaction(() -> insert());
        helper.rollback();
        helper.rollback();
        helper.commit();
        helper.commit();
        shouldThrow(() -> helper.transaction(() -> insert()));
        Assert.assertNull(helper.thrown());
        helper.commit();
        helper.commit();
        helper.rollback();
        helper.rollback();
        helper.transaction(() -> Assert.assertEquals(1, count()));
    }

    @Test
    public void testThrowsOnClosedSession() {
        TransactionHelper helper = new TransactionHelper(session);
        session.close();
        shouldThrow(() -> helper.begin());
        Assert.assertNotNull(helper.thrown());
        shouldThrow(() -> helper.transaction(() -> insert()));
        Assert.assertNotNull(helper.thrown());
    }
}
