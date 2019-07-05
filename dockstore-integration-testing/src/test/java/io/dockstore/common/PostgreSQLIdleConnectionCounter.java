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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.PostgreSQL91Dialect;

/**
 * @author gluu
 * @since 05/07/19
 */
public class PostgreSQLIdleConnectionCounter implements IdleConnectionCounter {
    public static final IdleConnectionCounter INSTANCE =
            new PostgreSQLIdleConnectionCounter();

    @Override
    public boolean appliesTo(Class<? extends Dialect> dialect) {
        return PostgreSQL91Dialect.class.isAssignableFrom( dialect );
    }

    @Override
    public int count(Connection connection) {
        try ( Statement statement = connection.createStatement() ) {
            try ( ResultSet resultSet = statement.executeQuery(
                    "SELECT COUNT(*) " +
                            "FROM pg_stat_activity " +
                            "WHERE state not LIKE '%idle%'" ) ) {
                while ( resultSet.next() ) {
                    return resultSet.getInt( 1 );
                }
                return 0;
            }
        }
        catch ( SQLException e ) {
            throw new IllegalStateException( e );
        }
    }
}
