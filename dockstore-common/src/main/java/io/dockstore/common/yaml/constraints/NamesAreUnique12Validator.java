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
import javax.validation.ConstraintValidatorContext;
import org.apache.commons.lang3.ObjectUtils;

/**
 * Validates that every entry of a `DockstoreYaml12` has a unique name.
 */
public class NamesAreUnique12Validator extends BaseConstraintValidator<NamesAreUnique12, DockstoreYaml12> {

    @Override
    public boolean isValidNotNull(final DockstoreYaml12 yaml, final ConstraintValidatorContext context) {

        List<Workflowish> entries = new ArrayList<>();
        Optional.ofNullable(yaml.getWorkflows()).ifPresent(entries::addAll);
        Optional.ofNullable(yaml.getNotebooks()).ifPresent(entries::addAll);
        Optional.ofNullable(yaml.getTools()).ifPresent(entries::addAll);

        // Create a set of entry names and check for duplicates in the process.
        Set<String> names = new HashSet<>();
        for (Workflowish entry: entries) {
            String name = ObjectUtils.firstNonNull(entry.getName(), "");
            if (!names.add(name)) {
                String reason = "at least two workflows or tools have " + ("".equals(name) ? "no name" : String.format("the same name '%s'", name));
                addConstraintViolation(context, getMessage(reason));
                return false;
            }
        }

        // If a service exists, check for any non-services without names.
        if (yaml.getService() != null && names.contains("")) {
            String reason = "a service always has no name, so any workflows or tools must be named";
            addConstraintViolation(context, getMessage(reason));
            return false;
        }

        return true;
    }

    private String getMessage(String reason) {
        return String.format("%s (%s)", NamesAreUnique12.MUST_HAVE_A_UNIQUE_NAME, reason);
    }
}
