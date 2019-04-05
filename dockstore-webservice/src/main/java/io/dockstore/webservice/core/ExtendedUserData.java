/*
 *    Copyright 2018 OICR
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package io.dockstore.webservice.core;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dockstore.webservice.permissions.PermissionsInterface;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import org.hibernate.Hibernate;

/**
 * This class will eventually hold all sorts of information about a user that is costly to calculate.
 */
@ApiModel(value = "ExtendedUserData", description = "Contains expensive data for end users for the dockstore")
public class ExtendedUserData {
    @ApiModelProperty(value = "Whether a user can change their username")
    private boolean canChangeUsername;

    public ExtendedUserData(User user, PermissionsInterface authorizer) {
        Hibernate.initialize(user.getEntries());
        this.canChangeUsername = user.getEntries().stream().noneMatch(Entry::getIsPublished) && !authorizer.isSharing(user) && user.getOrganizations().size() == 0;
    }

    /**
     * Returns whether a user has the current ability to change their username.
     * TODO: this may need to eventually become more sophisticated and take into account
     * shared content
     * // ignoring for now, this synthetic field may need to be calculated more sparingly and causes issues
     * @return true iff the user really can change their username
     */
    @JsonProperty
    public boolean canChangeUsername() {
        return canChangeUsername;
    }
}
