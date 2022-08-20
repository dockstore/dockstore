// TODO copyright

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
