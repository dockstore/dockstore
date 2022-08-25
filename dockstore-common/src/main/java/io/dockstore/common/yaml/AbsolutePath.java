// TODO copyright
package io.dockstore.common.yaml;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import javax.validation.Constraint;
import javax.validation.Payload;

/**
 * Defines a validation constraint annotation that verifies that a string is an absolute path.
 */
@Target({ ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE, ElementType.CONSTRUCTOR, ElementType.PARAMETER, ElementType.TYPE_PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = AbsolutePathValidator.class)
public @interface AbsolutePath {

    String MUST_BE_AN_ABSOLUTE_PATH = "must be an absolute path";

    String message () default MUST_BE_AN_ABSOLUTE_PATH;
    Class<?>[] groups () default {};
    Class<? extends Payload>[] payload () default {};
}
