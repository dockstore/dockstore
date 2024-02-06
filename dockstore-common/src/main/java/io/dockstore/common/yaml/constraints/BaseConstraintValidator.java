/*
 * Copyright 2023 OICR, UCSC
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

package io.dockstore.common.yaml.constraints;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Base class for most ConstraintValidators that has an empty initializer,
 * code that handles null values correctly (see below), and a helper method
 * addConstraintViolation() that creates a constraint violation with the
 * specified message.
 */
public abstract class BaseConstraintValidator<A extends java.lang.annotation.Annotation, T> implements ConstraintValidator<A, T> {
    @Override
    public void initialize(A constraintAnnotation) {
        // Intentionally empty
    }

    @Override
    public boolean isValid(T target, ConstraintValidatorContext context) {
        // Validations are cumulative and their order of application is not defined,
        // so the pattern for most validators is to consider `null` values as valid,
        // so that they can either be marked invalid by an accompanying @NotNull
        // annotation, or pass through, valid, without event.
        if (target == null) {
            return true;
        }
        return isValidNotNull(target, context);
    }

    protected abstract boolean isValidNotNull(T target, ConstraintValidatorContext context);

    protected void addConstraintViolation(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
    }
}
