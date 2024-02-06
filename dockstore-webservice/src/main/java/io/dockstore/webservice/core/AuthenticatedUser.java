/*
 * Copyright 2023 OICR and UCSC
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
 *
 */

package io.dockstore.webservice.core;

import java.util.Optional;

/**
 * Has the authenticated user, if any, of the current request to the API.
 */
public final class AuthenticatedUser {
    private static final ThreadLocal<User> AUTH_USER = new ThreadLocal<>();

    private AuthenticatedUser() {

    }

    public static void setUser(User user) {
        AUTH_USER.set(user);
    }

    public static Optional<User> getUser() {
        return Optional.ofNullable(AUTH_USER.get());
    }
}
