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
package io.dockstore.common.yaml.constraints;

import io.dockstore.common.yaml.DockstoreYaml12;
import jakarta.validation.ConstraintValidatorContext;
import java.util.List;

/**
 * Validates that a DockstoreYaml12 instance has at least one workflow, tool, or service
 */
public class HasEntry12Validator extends BaseConstraintValidator<HasEntry12, DockstoreYaml12> {

    @Override
    public boolean isValidNotNull(final DockstoreYaml12 value, final ConstraintValidatorContext context) {
        return value.getService() != null
            || !isEmpty(value.getWorkflows())
            || !isEmpty(value.getNotebooks())
            || !isEmpty(value.getTools());
    }

    private boolean isEmpty(List<?> list) {
        return list == null || list.isEmpty();
    }
}
