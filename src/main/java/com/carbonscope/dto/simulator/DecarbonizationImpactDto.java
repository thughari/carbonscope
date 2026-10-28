package com.carbonscope.dto.simulator;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DecarbonizationImpactDto {
    // Primary metrics matching selected accounting boundary (for UI/API compatibility)
    private double grossAvoidedEmissionsMt;
    private double cleanReplacementLifecycleEmissionsMt;
    private double netAvoidedEmissionsMt;
    private double netAvoidedEmissionsTonnes;
    private Double powerSectorEmissionsReductionPct;
    private Double nationalEmissionsReductionPct;
    private Double gridIntensityDeltaGco2PerKwh;
    private double cleanShareDeltaPct;

    // Direct Operational (Scope 1) metrics - strictly within power plant stack boundary
    private double avoidedDirectEmissionsMt;
    private Double simulatedDirectPowerEmissionsMt;
    private Double simulatedDirectGridIntensityGco2PerKwh;

    // Full Life-Cycle (Scope 1+2+3 / LCA) metrics - global cradle-to-grave boundary
    private double grossDisplacedLifecycleMt;
    private double cleanEmbodiedLifecycleMt;
    private double netAvoidedLifecycleMt;
    private Boolean netNegativeAbatement;
    private String netAbatementWarning;
    private String simulatedMetricsUnavailableReason;

    // Sourced IPCC AR5 Table A.III.2 / IPCC 2006 Sensitivity Interval (Min, Median, Max)
    private SensitivityIntervalDto sensitivityInterval;

    // Itemized results for multi-intervention composite scenarios
    private java.util.List<InterventionResultDto> itemizedInterventions;
    private double totalDisplacedFossilGenerationTwh;
    private double totalUnusedCleanGenerationTwh;
    private Boolean constraintsApplied;
    private String dispatchConstraintSummary;

    public boolean isNetNegativeAbatement() {
        return Boolean.TRUE.equals(netNegativeAbatement);
    }

    public boolean isConstraintsApplied() {
        return Boolean.TRUE.equals(constraintsApplied);
    }
}
