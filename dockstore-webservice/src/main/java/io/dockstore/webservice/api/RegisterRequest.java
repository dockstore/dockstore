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
package io.dockstore.webservice.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;

/**
 * This is an object to encapsulate a register request in an entity. Does not need to be stored in the database. Used for the body of
 * /containers/{containerId}/register
 *
 * @author xliu
 */
@ApiModel("RegisterRequest")
public class RegisterRequest {
    private boolean register;

    public RegisterRequest() {
    }

    public RegisterRequest(boolean register) {
        this.register = register;
    }

    @JsonProperty
    public boolean getRegister() {
        return register;
    }
}
