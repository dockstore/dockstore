/*
 *    Copyright 2020 OICR
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
package io.dockstore.common.yaml;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

import io.dockstore.common.DescriptorLanguageSubclass;

/**
 * A service defined in a 1.2 .dockstore.yml. It differs from 1.1 in that there is a subclass instead of a type property.
 * And the subclass property is an enum, not a string.
 *
 * Couldn't get SnakeYaml to directly load the YAML into the JavaBean with an enum, because our values are lower-case
 * and the enums are upper-case. See https://stackoverflow.com/questions/25079332/parse-enum-by-value-using-snakeyaml.
 */
public class Service12 extends AbstractYamlService {

    public  static final String SUBCLASS_ERROR = "subclass value must be \"docker-compose\", \"helm\", \"swarm\", \"kubernetes\", or \"n/a\"";
    private String subclass;
    private DescriptorLanguageSubclass descriptorLanguageSubclass;


    @NotNull(message = "Missing property \"subclass\"")
    @Pattern(regexp = "docker-compose|helm|swarm|kubernetes|n/a", message = SUBCLASS_ERROR)
    public String getSubclass() {
        return subclass;
    }

    public void setSubclass(String subclass) {
        this.subclass = subclass;
    }

    public DescriptorLanguageSubclass getDescriptorLanguageSubclass() {
        try {
            return DescriptorLanguageSubclass.convertShortNameStringToEnum(getSubclass());
        } catch (Exception ex) {
            return null;
        }
    }
}
