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

import io.dockstore.common.yaml.DockstoreYaml12AndUp;
import io.dockstore.common.yaml.Workflowish;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import org.apache.commons.lang3.ObjectUtils;

/**
 * Validates that every entry of a `DockstoreYaml12` has a unique name.
 */
public class NamesAreUniqueValidator implements ConstraintValidator<NamesAreUnique, DockstoreYaml12AndUp> {
    @Override
    public void initialize(final NamesAreUnique constraintAnnotation) {
        // Intentionally empty
    }

    @Override
    public boolean isValid(final DockstoreYaml12AndUp yaml, final ConstraintValidatorContext context) {
        if (yaml == null) {
            return true;
        }

        List<Workflowish> entries = yaml.getEntries();

        // Create a map of names to entries and check for duplicate names in the process.
        Map<String, Workflowish> namesToEntries = new HashMap<>();
        for (Workflowish entry: entries) {
            String name = ObjectUtils.firstNonNull(entry.getName(), "");
            if (namesToEntries.containsKey(name)) {
                Workflowish otherEntry = namesToEntries.get(name);
                String reason = String.format("%s have %s",
                    getSubject(entry, otherEntry),
                    "".equals(name) ? "no name" : String.format("the same name '%s'", name));
                addConstraintViolation(context, reason);
                return false;
            }
            namesToEntries.put(name, entry);
        }

        return true;
    }

    private String getSubject(Workflowish a, Workflowish b) {
        String termA = a.getTerm(false);
        String termB = b.getTerm(false);
        if (Objects.equals(termA, termB)) {
            return String.format("at least two %s", a.getTerm(true));
        } else {
            return String.format("a %s and a %s", termA, termB);
        }
    }

    private static void addConstraintViolation(final ConstraintValidatorContext context, String reason) {
        String msg = String.format("%s (%s)", NamesAreUnique.MUST_HAVE_A_UNIQUE_NAME, reason);
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(msg).addConstraintViolation();
    }
}
