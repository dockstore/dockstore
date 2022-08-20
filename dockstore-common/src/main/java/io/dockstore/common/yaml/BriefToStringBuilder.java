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

public class BriefToStringBuilder extends ToStringBuilder {
    
    public BriefToStringBuilder(Object object) {
        super(object, ToStringStyle.NO_CLASS_NAME_STYLE);
    }

    public BriefToStringBuilder append(String fieldName, Object value) {
        if (value != null) {
            super.append(fieldName, value);
        }
        return this;
    }

    public BriefToStringBuilder append(String fieldName, Object[] values) {
        if (values != null) {
            super.append(fieldName, values);
        }
        return this;
    }
}
