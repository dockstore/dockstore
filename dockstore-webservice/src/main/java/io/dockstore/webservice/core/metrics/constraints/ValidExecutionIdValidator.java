/*
 * Copyright 2024 OICR and UCSC
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

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;

/**
 * Validates that execution ID.
 */
public class ValidExecutionIdValidator implements ConstraintValidator<ValidExecutionId, String> {
    private static final String VALID_EXECUTION_ID_REGEX = "[a-zA-Z0-9]+([-_][a-zA-Z0-9]+)*+";
    private static final Pattern VALID_EXECUTION_ID_PATTERN = Pattern.compile(VALID_EXECUTION_ID_REGEX);
    private static final int MAX_LENGTH = 100;

    @Override
    public boolean isValid(final String executionId, final ConstraintValidatorContext context) {
        return StringUtils.isNotBlank(executionId) && VALID_EXECUTION_ID_PATTERN.matcher(executionId).matches() && executionId.length() <= MAX_LENGTH;
    }
}
