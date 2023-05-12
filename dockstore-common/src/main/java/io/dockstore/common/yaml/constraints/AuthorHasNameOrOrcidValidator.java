/*
 * Copyright 2022 OICR and UCSC
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

import io.dockstore.common.yaml.YamlAuthor;
import jakarta.validation.ConstraintValidatorContext;
import org.apache.commons.lang3.StringUtils;

/**
 * Validates that an author has a non-empty name or ORCID.
 */
public class AuthorHasNameOrOrcidValidator extends BaseConstraintValidator<AuthorHasNameOrOrcid, YamlAuthor> {

    @Override
    public boolean isValidNotNull(final YamlAuthor author, final ConstraintValidatorContext context) {
        return StringUtils.isNotEmpty(author.getName()) || StringUtils.isNotEmpty(author.getOrcid());
    }
}
