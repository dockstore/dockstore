/*
 * Copyright 2026 OICR and UCSC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.dockstore.webservice.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The Dockstore entry ID and, optionally, version ID that correspond to a TRS ID.
 */
@Schema(description = "The Dockstore entry ID and, if applicable, version ID that correspond to a TRS ID")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EntryAndVersionIds(
    @Schema(description = "Dockstore entry ID", requiredMode = Schema.RequiredMode.REQUIRED) Long entryId,
    @Schema(description = "Dockstore version ID, present only if a TRS version ID was mapped") Long versionId) {
}
