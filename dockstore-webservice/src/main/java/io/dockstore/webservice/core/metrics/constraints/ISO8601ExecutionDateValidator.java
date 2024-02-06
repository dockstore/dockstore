/*
 * Copyright 2023 OICR and UCSC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *           http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.dockstore.webservice.core.metrics.constraints;

import io.dockstore.common.metrics.FormatCheckHelper;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validates that execution time is in ISO 8601 UTC date format
 */
public class ISO8601ExecutionDateValidator implements ConstraintValidator<ISO8601ExecutionDate, String> {

    @Override
    public boolean isValid(final String executionTime, final ConstraintValidatorContext context) {
        if (executionTime == null) {
            return false;
        }
        return FormatCheckHelper.checkExecutionDateISO8601Format(executionTime).isPresent();
    }
}
