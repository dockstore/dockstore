/*
 * Copyright 2019 OICR
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.dockstore.webservice.core;

/**
 * @author gluu
 * @since 2019-11-11
 */
public enum DescriptionSource {
    // as auto-detected at the root of the repository
    README,
    // as overridden by a user in a .dockstore.yml
    CUSTOM_README,
    //  as harvested from the workflow files themselves
    DESCRIPTOR
}
