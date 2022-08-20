// TODO copyright
package io.dockstore.common.yaml;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import javax.validation.Constraint;
import javax.validation.Payload;

@Target({ ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = AuthorNameOrOrcidValidator.class)
public @interface AuthorNameOrOrcid {

    String AUTHOR_REQUIRES_NAME_OR_ORCID = "must have a name or an ORCID id";

    String message () default AUTHOR_REQUIRES_NAME_OR_ORCID;
    Class<?>[] groups () default {};
    Class<? extends Payload>[] payload () default {};
}
