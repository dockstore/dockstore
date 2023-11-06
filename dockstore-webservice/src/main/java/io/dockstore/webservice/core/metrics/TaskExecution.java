/*
 * Copyright 2023 OICR and UCSC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *           http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.dockstore.webservice.core.metrics;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * This is an object to encapsulate a workflow task run execution metrics data in an entity. Does not need to be stored in the database.
 */
@Schema(name = "TaskExecution", description = "Metrics of a workflow task execution on a platform", allOf = AbstractRunExecution.class)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class TaskExecution extends AbstractRunExecution {

}
