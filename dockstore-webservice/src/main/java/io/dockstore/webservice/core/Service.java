/*
 *    Copyright 2019 OICR
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

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

import io.swagger.annotations.ApiModel;

/**
 * Services look an awful lot like a special case of a workflow with a crappy workflow language
 */
@ApiModel(value = "Service", description = "This describes one service in the dockstore")
@DiscriminatorValue("service")
@Entity
public class Service extends Workflow {


}
