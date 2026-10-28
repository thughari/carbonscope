package com.carbonscope.dto.simulator;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Itemized output for an individual intervention within a composite scenario.
 * Tracks requested vs. dispatched generation, unused clean energy, and attributable emissions.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterventionResultDto {
    private int interventionIndex;
    private String interventionId;
    private String cleanSource;
    private String cleanSourceDisplayName;
    private String targetDisplacedFuel;
    private String targetFuelDisplayName;

    // Physical generation metrics (TWh)
    private double requestedCleanGenerationTwh;
    private double displacedFossilGenerationTwh;
    private double unusedCleanGenerationTwh;

    // Avoided emissions (Mt CO2 / Mt CO2e)
    private double grossAvoidedMt;
    private double cleanReplacementLifecycleMt;
    private double netAvoidedMt;

    // Sourced sensitivity interval for this specific intervention
    private double sensitivityMinMt;
    private double sensitivityMedianMt;
    private double sensitivityMaxMt;

    // Constraint and dispatch flags
    private boolean isCapped;
    private String constraintNote;
}
