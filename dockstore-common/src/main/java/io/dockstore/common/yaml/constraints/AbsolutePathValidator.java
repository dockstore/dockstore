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
 */

package io.dockstore.common.yaml.constraints;

import jakarta.validation.ConstraintValidatorContext;

/**
 * Validates the file path is an absolute path.
 */
public class AbsolutePathValidator extends BaseConstraintValidator<AbsolutePath, String> {

    @Override
    public boolean isValidNotNull(final String path, final ConstraintValidatorContext context) {
        return path.startsWith("/");
    }
}
