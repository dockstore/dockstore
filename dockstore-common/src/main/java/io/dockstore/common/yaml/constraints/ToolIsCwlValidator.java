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

import io.dockstore.common.DescriptorLanguage;
import io.dockstore.common.yaml.YamlTool;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validates that an apptool has the descriptor language CWL.
 */
public class ToolIsCwlValidator extends BaseConstraintValidator<ToolIsCwl, YamlTool> {

    @Override
    public boolean isValidNotNull(final YamlTool tool, final ConstraintValidatorContext context) {
        if (!DescriptorLanguage.CWL.getShortName().equalsIgnoreCase(tool.getSubclass())) {
            // create a violation that includes the 'subclass' property in the violation path.
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate()).addPropertyNode("subclass").addConstraintViolation();
            return false;
        }
        return true;
    }
}
