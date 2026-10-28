package com.carbonscope.dto.simulator;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecarbonizationResultsDto {
    private double totalGenerationTwh;
    private double simulatedFossilGenerationTwh;
    private double simulatedTargetFuelGenerationTwh;
    private double simulatedCleanGenerationTwh;
    private double simulatedCleanPowerSharePct;
    private Double simulatedPowerSectorEmissionsMt;
    private Double simulatedGridCarbonIntensityGco2PerKwh;
    private Double simulatedNationalTotalEmissionsMt;
    private String emissionsBoundaryNote;

    // Composite and constraint dispatch metrics (TWh)
    private double totalRequestedCleanGenerationTwh;
    private double totalDisplacedFossilGenerationTwh;
    private double totalUnusedCleanGenerationTwh;
    private double simulatedCoalGenerationTwh;
    private double simulatedGasGenerationTwh;
    private double simulatedOilGenerationTwh;
    private boolean constraintsApplied;
    private String constraintEnforcementSummary;
}
