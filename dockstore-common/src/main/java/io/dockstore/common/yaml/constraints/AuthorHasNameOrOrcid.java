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

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines the `AuthorHasNameOrOrcid` constraint annotation, which
 * is valid if a `YamlAuthor` has either a non-empty name or ORCID id.
 */
@Target({ ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE, ElementType.CONSTRUCTOR, ElementType.PARAMETER, ElementType.TYPE_USE })
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = AuthorHasNameOrOrcidValidator.class)
public @interface AuthorHasNameOrOrcid {

    String AUTHOR_REQUIRES_NAME_OR_ORCID = "must have a name or an ORCID id";

    String message () default AUTHOR_REQUIRES_NAME_OR_ORCID;
    Class<?>[] groups () default {};
    Class<? extends Payload>[] payload () default {};
}
