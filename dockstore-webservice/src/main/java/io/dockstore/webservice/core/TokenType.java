/*
 * Copyright (C) 2015 Consonance
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
package io.dockstore.webservice.core;

import io.swagger.annotations.ApiModel;

/**
 * Enumerates the sources for access tokens for the dockstore
 * @author dyuen
 */
@ApiModel(description = "Enumerates the sources for access tokens for the dockstore")
public enum TokenType {
    QUAY_IO("quay.io"), GITHUB_COM("github.com"), DOCKSTORE("dockstore"), BITBUCKET_ORG("bitbucket.org");
    private final String friendlyName;

    TokenType(String friendlyName) {
        this.friendlyName = friendlyName;
    }

    @Override
    public String toString() {
        return this.friendlyName;
    }
}
