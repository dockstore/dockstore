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

import io.dockstore.common.yaml.DockstoreYaml12;
import io.dockstore.common.yaml.Workflowish;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

/**
 * Validates that every entry of a `DockstoreYaml12` has a unique name.
 */
public class NamesAreUniqueValidator implements ConstraintValidator<NamesAreUnique, DockstoreYaml12> {
    @Override
    public void initialize(final NamesAreUnique constraintAnnotation) {
        // Intentionally empty
    }

    @Override
    public boolean isValid(final DockstoreYaml12 yaml, final ConstraintValidatorContext context) {
        if (yaml == null) { 
            return true;
        }

        List<Workflowish> entries = new ArrayList<>();
        Optional.ofNullable(yaml.getTools()).ifPresent(entries::addAll);
        Optional.ofNullable(yaml.getWorkflows()).ifPresent(entries::addAll);

        Set<String> names = new HashSet<>();
        for (Workflowish entry: entries) {
            String name = entry.getName();
            if (name == null) {
                name = "";
            }
            if (!names.add(name)) {
                String reason = "".equals(name) ? "At least two workflows or tools have no name." : String.format("At least two workflows or tools have the same name '%s'.", name);
                addConstraintViolation(context, reason);
                return false;
            }
        }

        if (yaml.getService() != null && names.contains("")) {
            String reason = "A service always has no name, so any workflows or tools must be named.";
            addConstraintViolation(context, reason);
            return false;
        }

        return true;
    }

    private static void addConstraintViolation(final ConstraintValidatorContext context, String reason) {
        String msg = String.format("%s (%s)", NamesAreUnique.MUST_HAVE_A_UNIQUE_NAME, reason);
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(msg).addConstraintViolation();
    }
}
