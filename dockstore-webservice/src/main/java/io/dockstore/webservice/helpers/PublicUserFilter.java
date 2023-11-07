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

public class PublicUserFilter extends SimpleBeanPropertyFilter {

    private BeanPropertyWriter propertyWriter = new AdminCuratorPropertyWriter();

    @Override
    public void serializeAsField(Object pojo, JsonGenerator jgen, SerializerProvider provider, PropertyWriter writer) throws Exception {
        if (writer.getName().equals("isAdmin") || writer.getName().equals("curator")) {
            super.serializeAsField(pojo, jgen, provider, propertyWriter);
        }
        super.serializeAsField(pojo, jgen, provider, writer);
    }

    private static class AdminCuratorPropertyWriter extends BeanPropertyWriter {
        @Override
        public void serializeAsField(Object bean, JsonGenerator gen, SerializerProvider prov) throws Exception {
            gen.writeFieldName("isAdmin");
            gen.writeBoolean(false);
            gen.writeFieldName("curator");
            gen.writeBoolean(false);
        }
    }
}
