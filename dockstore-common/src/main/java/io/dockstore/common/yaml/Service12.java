/*
 *    Copyright 2020 OICR
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
package io.dockstore.common.yaml;

import io.dockstore.common.DescriptorLanguageSubclass;
import javax.validation.constraints.NotNull;

/**
 * A service defined in a 1.2 .dockstore.yml. It differs from 1.1 in that there is a subclass instead of a type property.
 * And the subclass property is an enum, not a string.
 *
 */
public class Service12 extends AbstractYamlService implements Workflowish {

    public static final String MISSING_SUBCLASS = "Missing property \"subclass\"";

    private DescriptorLanguageSubclass subclass;

    @NotNull(message = MISSING_SUBCLASS)
    public DescriptorLanguageSubclass getSubclass() {
        return subclass;
    }

    public void setSubclass(final DescriptorLanguageSubclass subclass) {
        this.subclass = subclass;
    }

}
