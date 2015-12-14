/*
 * Copyright (C) 2015 Collaboratory
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.dockstore.common;

/**
 * 
 * This describes all keys available in Dockstore config files.
 *
 * @author xliu
 */
public class Constants {
    public static final String WEBSERVICE_BASE_PATH = "webservice.base_path";
    public static final String WEBSERVICE_TOKEN_USER_1 = "webservice.tokenUser1"; // Dummy dockstore token for user 1
    public static final String WEBSERVICE_TOKEN_USER_2 = "webservice.tokenUser2"; // Dummy dockstore token for user 2

    public static final String POSTGRES_HOST = "database.postgresHost";
    public static final String POSTGRES_USERNAME = "database.postgresUser";
    public static final String POSTGRES_PASSWORD = "database.postgresPass";
    public static final String POSTGRES_DBNAME = "database.postgresDBName";
    public static final String POSTGRES_MAX_CONNECTIONS = "database.maxConnections";
}
