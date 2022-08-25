/*
 *    Copyright 2022 OICR and UCSC
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

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

/**
 * Validates that a string is an absolute path.
 * Similar to the built-in constraints (such as @Pattern), null is valid,
 * so that this constraint can be applied to optional values.
 */
public class AbsolutePathValidator implements ConstraintValidator<AbsolutePath, String> {
    @Override
    public void initialize(final AbsolutePath constraintAnnotation) {
        // Intentionally empty
    }

    @Override
    public boolean isValid(final String value, final ConstraintValidatorContext context) {
        return value == null || value.startsWith("/");
    }
}
