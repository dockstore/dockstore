// TODO add copyright header

package io.dockstore.common.yaml.constraints;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

/**
 * Base class for most ConstraintValidators.
 */
public abstract class BaseConstraintValidator<AnnotationT extends java.lang.annotation.Annotation, TargetT> implements ConstraintValidator<AnnotationT, TargetT> {
    @Override
    public void initialize(final AnnotationT constraintAnnotation) {
        // Intentionally empty
    }

    protected void addConstraintViolation(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
    }
}
