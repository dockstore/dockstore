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

package io.dockstore.common;

/**
 * The frontend will need to convert the enums to friendly values
 */
public enum Partner {
    GALAXY,
    TERRA,
    DNA_STACK,
    DNA_NEXUS,
    CGC,
    NHLBI_BIODATA_CATALYST,
    ANVIL,
    CAVATICA,
    NEXTFLOW_TOWER,
    ELWAZI,
    AGC,
    OTHER, // This is meant for platforms that want to submit Metrics to Dockstore, but they're not officially a Partner
    ALL; // Captures all platforms for submitting Metrics aggregated across all platforms

    /**
     * Returns true if the partner is actually an individual partner and not a generic value like Partner.ALL
     * @return
     */
    public boolean isActualPartner() {
        return this != ALL;
    }
}
