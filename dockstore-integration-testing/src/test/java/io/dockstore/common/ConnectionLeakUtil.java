/*
 *
 *  *    Copyright 2019 OICR
 *  *
 *  *    Licensed under the Apache License, Version 2.0 (the "License");
 *  *    you may not use this file except in compliance with the License.
 *  *    You may obtain a copy of the License at
 *  *
 *  *        http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  *    Unless required by applicable law or agreed to in writing, software
 *  *    distributed under the License is distributed on an "AS IS" BASIS,
 *  *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  *    See the License for the specific language governing permissions and
 *  *    limitations under the License.
 *
 */

package io.dockstore.common;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SortedMap;

import com.codahale.metrics.Gauge;
import io.dockstore.webservice.DockstoreWebserviceConfiguration;
import io.dropwizard.testing.DropwizardTestSupport;
import org.junit.Assert;

/**
 * @author gluu
 * @since 1.7.0
 */
public class ConnectionLeakUtil {
    private String url;
    private String user;
    private String password;
    private IdleConnectionCounter connectionCounter = PostgreSQLIdleConnectionCounter.INSTANCE;


    private int connectionLeakCount;

    public ConnectionLeakUtil(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
        connectionLeakCount = countConnectionLeaks();
    }

    public void assertNoLeaks() throws Exception {
        if ( connectionCounter != null ) {
            int currentConnectionLeakCount = countConnectionLeaks();
            int diff = currentConnectionLeakCount - connectionLeakCount;
            // This should be 0, but it's always one. Either this code is wrong or that there's always a connection leak of 1
            if ( diff > 0 ) {
                throw new Exception(
                        String.format(
                                "%d connection(s) have been leaked! Previous leak count: %d, Current leak count: %d",
                                diff,
                                connectionLeakCount,
                                currentConnectionLeakCount
                        )
                );
            }
        }
    }

    private int countConnectionLeaks() {
        try ( Connection connection = newConnection() ) {
            return connectionCounter.count( connection );
        }
        catch ( SQLException e ) {
            throw new IllegalStateException( e );
        }
    }

    private Connection newConnection() {
        try {
            return DriverManager.getConnection(
                    url,
                    user,
                    password
            );
        }
        catch ( SQLException e ) {
            throw new IllegalStateException( e );
        }
    }
}
