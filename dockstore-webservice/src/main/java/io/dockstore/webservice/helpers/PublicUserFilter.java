/*
 * Copyright 2023 OICR and UCSC
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
 *
 */

package io.dockstore.webservice.helpers;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.PropertyWriter;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import io.dockstore.webservice.core.AuthenticatedUser;
import io.dockstore.webservice.core.User;

/**
 * A Jackson JSON filter that writes out false for the User#isAdmin and User#curator properties, if the requesting user is not an admin and does not
 * match the user making the request.
 */
public class PublicUserFilter extends SimpleBeanPropertyFilter {

    protected static final String ADMIN_PROPERTY = "isAdmin";
    protected static final String CURATOR_PROPERTY = "curator";
    private BeanPropertyWriter maskCuratorAndAdminWriter = new MaskAdminCuratorPropertyWriter();


    @Override
    public void serializeAsField(Object pojo, JsonGenerator jgen, SerializerProvider provider, PropertyWriter writer) throws Exception {
        User user = (User)pojo;
        super.serializeAsField(pojo, jgen, provider, getPropertyWriter(user, writer));
    }

    /**
     * If the user being serialized is not the authenticated user, and the authenticated user is not an admin, return a new PropertyWriter
     * that always serializes false for isAdmin and curator properties.
     * @param userToSerialize
     * @param existingWriter
     * @return
     */
    private PropertyWriter getPropertyWriter(User userToSerialize, PropertyWriter existingWriter) {
        if ((ADMIN_PROPERTY.equals(existingWriter.getName()) || CURATOR_PROPERTY.equals(existingWriter.getName())) && !showAdminAndCuratorValues(userToSerialize)) {
            return maskCuratorAndAdminWriter;
        }
        return existingWriter;
    }

    private boolean showAdminAndCuratorValues(final User toSerialize) {
        return AuthenticatedUser.getUser().map(authUser -> authUser.getId() == toSerialize.getId() || authUser.getIsAdmin()).orElse(false);
    }

    /**
     * Writer that serializes the isAdmin and curator property values as false, regardless of their actual values.
     */
    private static final class MaskAdminCuratorPropertyWriter extends BeanPropertyWriter {
        @Override
        public void serializeAsField(Object bean, JsonGenerator gen, SerializerProvider prov) throws Exception {
            gen.writeFieldName(ADMIN_PROPERTY);
            gen.writeBoolean(false);
            gen.writeFieldName(CURATOR_PROPERTY);
            gen.writeBoolean(false);
        }
    }
}
