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

package io.dockstore.common.yaml;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * Adapts `ToStringBuilder` to construct a short text representation of
 * the values of an object's fields, extending the methods that format
 * fields with object or object array values so that null values are not
 * displayed.
 *
 * <p>Used to create short text representations of the YAML objects
 * that are suitable for inclusion in error messages.
 */
public class BriefToStringBuilder extends ToStringBuilder {
    
    public BriefToStringBuilder(Object object) {
        super(object, ToStringStyle.NO_CLASS_NAME_STYLE);
    }

    @Override
    public BriefToStringBuilder append(String fieldName, Object value) {
        if (value != null) {
            super.append(fieldName, value);
        }
        return this;
    }

    @Override
    public BriefToStringBuilder append(String fieldName, Object[] values) {
        if (values != null) {
            super.append(fieldName, values);
        }
        return this;
    }
}
