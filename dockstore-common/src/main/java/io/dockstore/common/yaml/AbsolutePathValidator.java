// TODO copyright
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
