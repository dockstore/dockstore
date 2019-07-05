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

import org.hibernate.dialect.Dialect;

/**
 * @author gluu
 * @since 05/07/19
 */
public interface IdleConnectionCounter {
    /**
     * Specifies which Dialect the counter applies to.
     *
     * @param dialect dialect
     *
     * @return applicability.
     */
    boolean appliesTo(Class<? extends Dialect> dialect);

    /**
     * Count the number of idle connections.
     *
     * @param connection current JDBC connection to be used for querying the number of idle connections.
     *
     * @return idle connection count.
     */
    int count(Connection connection);
}
