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
 * Defines the `NamesAreUnique12` constraint annotation, which
 * is valid if every entry of a `DockstoreYaml12` has a unique name.
 */
@Target({ ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE, ElementType.CONSTRUCTOR, ElementType.PARAMETER, ElementType.TYPE_USE })
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = NamesAreUnique12Validator.class)
public @interface NamesAreUnique12 {

    String MUST_HAVE_A_UNIQUE_NAME = "must have a unique name for each workflow and tool";

    String message () default MUST_HAVE_A_UNIQUE_NAME;
    Class<?>[] groups () default {};
    Class<? extends Payload>[] payload () default {};
}
