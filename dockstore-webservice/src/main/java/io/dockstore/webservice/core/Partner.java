package io.dockstore.webservice.core;

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
