package com.carbonscope.dto.simulator;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents an individual intervention within a multi-intervention composite scenario.
 * Allows pairing a specific clean generation technology addition with a targeted fossil fuel reduction.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScenarioInterventionDto {
    private String id;
    
    /**
     * Clean generation source: 'SOLAR_PV', 'WIND_ONSHORE', 'WIND_OFFSHORE', 'NUCLEAR', 'HYDRO'.
     */
    private String cleanSource;
    
    /**
     * Generation amount requested.
     */
    private double generationAmount;
    
    /**
     * Generation unit: 'TWH' or 'GWH'. Defaults to 'TWH'.
     */
    private String generationUnit;
    
    /**
     * Targeted fossil fuel to displace: 'COAL', 'FOSSIL_GAS', 'OIL', or 'GRID_FOSSIL_MIX'.
     */
    private String targetDisplacedFuel;
    
    // Optional custom factor overrides for this specific intervention
    private Double customDisplacedEmissionFactor;
    private String customDisplacedFactorSource;
    private Double customReplacementEmissionFactor;
    private String customReplacementFactorSource;
}
