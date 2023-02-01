// TODO add copyright header

package io.dockstore.common.yaml.constraints;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

/**
 * Base class for most ConstraintValidators.
 */
public abstract class BaseConstraintValidator<AnnotationT extends java.lang.annotation.Annotation, TargetT> implements ConstraintValidator<AnnotationT, TargetT> {
    @Override
    public void initialize(AnnotationT constraintAnnotation) {
        // Intentionally empty
    }

    @Override
    public boolean isValid(TargetT target, ConstraintValidatorContext context) {
        if (target == null) {
            return true;
        }
        return isValidNotNull(target, context);
    }

    protected abstract boolean isValidNotNull(TargetT target, ConstraintValidatorContext context);

    protected void addConstraintViolation(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
    }
}
