// TODO copyright
package io.dockstore.common.yaml;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import org.apache.commons.lang3.StringUtils;

/**
 * Validates that an author has a non-empty name or ORCID.
 */
public class AuthorNameOrOrcidValidator implements ConstraintValidator<AuthorNameOrOrcid, YamlAuthor> {
    @Override
    public void initialize(final AuthorNameOrOrcid constraintAnnotation) {
        // Intentionally empty
    }

    @Override
    public boolean isValid(final YamlAuthor author, final ConstraintValidatorContext context) {
        return !StringUtils.isEmpty(author.getName()) || !StringUtils.isEmpty(author.getOrcid());
    }
}
