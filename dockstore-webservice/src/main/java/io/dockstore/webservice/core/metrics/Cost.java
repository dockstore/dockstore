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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "Cost", description = "Cost represents a monetary amount in USD")
public class Cost {
    private static final String DEFAULT_CURRENCY = "USD";

    // We may eventually want to allow different currencies to be submitted
    @Schema(description = "The currency of the cost value", defaultValue = DEFAULT_CURRENCY)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String currency = DEFAULT_CURRENCY;

    @Schema(description = "The numerical value of the cost", example = "5.99")
    private Double value;

    @JsonCreator
    public Cost(@JsonProperty("value") Double value) {
        this.value = value;
    }

    public String getCurrency() {
        return currency;
    }


    public Double getValue() {
        return value;
    }

    public void setValue(Double value) {
        this.value = value;
    }
}
