package com.carbonscope.dto.simulator;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScenarioAssumptionsDto {
    private String displacedFuel;
    private double displacedGenerationTwh;
    private double displacedGenerationGwh;
    private double displacedEmissionFactorGco2PerKwh;
    private String displacedFactorSource;
    private int displacedFactorPublicationYear;
    private String displacedFactorBoundary;
    private String displacedFactorRange;
    private String displacedFactorDerivationMethod;
    private String displacedFactorHeatingValueBasis;

    private String replacementSource;
    private double replacementGenerationTwh;
    private double replacementGenerationGwh;
    private double replacementEmissionFactorGco2PerKwh;
    private String replacementFactorSource;
    private int replacementFactorPublicationYear;
    private String replacementFactorBoundary;
    private String replacementFactorRange;

    private String displacementModelingType;
    private String gwpConvention;
    private String geographicAccountingScope;
    private String dispatchEquivalenceCaveat;
    private java.util.List<String> documentedExclusions;

    private boolean customDisplacedFactor;
    private String customDisplacedFactorSource;
    private boolean customReplacementFactor;
    private String customReplacementFactorSource;

    // Multi-intervention composite scenario metadata
    private java.util.List<ScenarioInterventionDto> configuredInterventions;
    private ScenarioConstraintsDto configuredConstraints;
    private String dispatchConstraintDescription;
}
