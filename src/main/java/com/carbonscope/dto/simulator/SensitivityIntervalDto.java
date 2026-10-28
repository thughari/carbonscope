package com.carbonscope.dto.simulator;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Deterministic sensitivity interval representing peer-reviewed uncertainty bounds
 * in technology emission factors (IPCC AR5 WGIII Annex III Table A.III.2 / IPCC 2006).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SensitivityIntervalDto {
    /**
     * Lower bound of avoided emissions (conservative estimate).
     * In lifecycle mode: min displaced fossil factor combined with max clean supply-chain factor.
     * In operational mode: highest plant efficiency / lowest emission factor.
     */
    private double minAvoidedEmissionsMt;

    /**
     * Median avoided emissions (central estimate).
     * In lifecycle mode: median IPCC AR5 Table A.III.2 factors.
     * In operational mode: reference IPCC 2006 stationary combustion factors.
     */
    private double medianAvoidedEmissionsMt;

    /**
     * Upper bound of avoided emissions (optimistic estimate).
     * In lifecycle mode: max displaced fossil factor combined with min clean supply-chain factor.
     * In operational mode: lowest plant efficiency / highest emission factor.
     */
    private double maxAvoidedEmissionsMt;

    // Sourced factor bounds for displaced fuel (g/kWh)
    private double displacedFactorMinGco2PerKwh;
    private double displacedFactorMedianGco2PerKwh;
    private double displacedFactorMaxGco2PerKwh;

    // Sourced factor bounds for replacement clean technology (g/kWh)
    private double replacementFactorMinGco2PerKwh;
    private double replacementFactorMedianGco2PerKwh;
    private double replacementFactorMaxGco2PerKwh;

    // Provenance and uncertainty metadata
    private String emissionFactorUnit;
    private String emissionsUnit;
    private String uncertaintyDescription;
    private String sourceCitation;
    private int publicationYear;
    private String accountingBoundary;
}
