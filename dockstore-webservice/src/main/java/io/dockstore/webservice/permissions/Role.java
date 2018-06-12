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

package io.dockstore.webservice.permissions;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public enum Role {
    // More privileged roles should be first, as we compare with ordinal
    OWNER(Action.WRITE, Action.READ, Action.DELETE, Action.SHARE),
    WRITER(Action.WRITE, Action.READ),
    READER(Action.READ);

    private final Set<Action> actions;

    Role(Action... actions) {
        this.actions = new HashSet<>(Arrays.asList(actions));
    }

    boolean hasAction(Action action) {
        return this.actions.contains(action);
    }

    public enum Action {
        WRITE("write"),
        READ("read"),
        DELETE("delete"),
        SHARE("share");

        private final String name;

        Action(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return this.name;
        }
    }
}
