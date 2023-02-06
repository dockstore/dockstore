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
import io.dockstore.common.EntryType;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

/**
 * Validates a descriptor language short name.
 */
public class ValidDescriptorLanguageValidator implements ConstraintValidator<ValidDescriptorLanguage, String> {

    private EntryType entryType;

    @Override
    public void initialize(ValidDescriptorLanguage annotation) {
        entryType = annotation.entryType();
    }

    @Override
    public boolean isValid(final String shortName, final ConstraintValidatorContext context) {
        if (shortName == null) {
            return true;
        }
        try {
            return DescriptorLanguage.convertShortStringToEnum(shortName).getEntryTypes().contains(entryType);
        } catch (UnsupportedOperationException ex) {
            return false;
        }
    }
}
