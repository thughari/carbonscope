package com.carbonscope.dto.simulator;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecarbonizationScenarioRequest {
    private String countryCode;
    private String displacedFuel;
    private String replacementCleanSource;
    private Double generationAmount;
    private String generationUnit;
    
    /**
     * Accounting boundary for the calculation:
     * - "OPERATIONAL_SCOPE_1": Direct combustion emissions at power plant stack (compatible with Ember 2024 & UNFCCC).
     * - "LIFECYCLE_SCOPE_1_2_3": Cradle-to-grave lifecycle emissions (IPCC AR5 WGIII Annex III Table A.III.2 LCA).
     * Defaults to "OPERATIONAL_SCOPE_1" if null.
     */
    private String accountingBoundary;

    private Double customDisplacedEmissionFactor;
    private String customDisplacedFactorSource;

    private Double customReplacementEmissionFactor;
    private String customReplacementFactorSource;

    /**
     * Optional list of multiple interventions for a composite scenario.
     * If provided and non-empty, this supersedes single-intervention fields.
     */
    private java.util.List<ScenarioInterventionDto> interventions;

    /**
     * Optional generation and dispatch constraints (minimum residual fossil baseload, max clean limit, etc.).
     */
    private ScenarioConstraintsDto constraints;
}
