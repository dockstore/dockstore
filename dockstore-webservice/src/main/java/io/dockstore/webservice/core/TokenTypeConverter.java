/*
 *    Copyright 2017 OICR
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package io.dockstore.webservice.core;

import com.google.common.collect.Lists;
import java.util.Optional;
import javax.persistence.AttributeConverter;
import javax.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Converter(autoApply = true)
public class TokenTypeConverter implements AttributeConverter<TokenType, String> {
    private static final Logger LOG = LoggerFactory.getLogger(TokenTypeConverter.class);

    @Override
    public String convertToDatabaseColumn(TokenType attribute) {
        return attribute.toString();
    }

    @Override
    public TokenType convertToEntityAttribute(String dbData) {
        Optional<TokenType> first = Lists.newArrayList(TokenType.values()).stream().filter(val -> val.toString().equals(dbData))
            .findFirst();
        if (first.isPresent()) {
            return first.get();
        } else {
            LOG.error("could not convert token type: " + dbData);
            return null;
        }
    }
}
