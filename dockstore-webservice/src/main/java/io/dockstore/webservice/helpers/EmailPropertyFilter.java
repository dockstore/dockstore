package io.dockstore.webservice.helpers;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.PropertyWriter;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import io.dockstore.webservice.core.User.Profile;
import org.apache.commons.validator.routines.EmailValidator;

/**
 * Conditionally removes Strings that look like email addresses
 * Example from <a href="https://www.baeldung.com/jackson-serialize-field-custom-criteria">...</a>
 */
public class EmailPropertyFilter extends SimpleBeanPropertyFilter {
    EmailValidator emailValidator = EmailValidator.getInstance();

    @Override
    public void serializeAsField(Object pojo, JsonGenerator jgen, SerializerProvider provider, PropertyWriter writer) throws Exception {
        if (include(writer)) {
            if (!writer.getName().equals("username")) {
                writer.serializeAsField(pojo, jgen, provider);
                return;
            }
            Profile profile = (Profile) pojo;

            if (!emailValidator.isValid(profile.username)) {
                writer.serializeAsField(pojo, jgen, provider);
            }
        } else if (!jgen.canOmitFields()) { // since 2.3
            writer.serializeAsOmittedField(pojo, jgen, provider);
        }
    }

    @Override
    protected boolean include(BeanPropertyWriter writer) {
        return true;
    }
    @Override
    protected boolean include(PropertyWriter writer) {
        return true;
    }
}
