package com.carbonscope.dto.policy;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScenarioNdcComparisonDto {
    private String countryCode;
    private String countryName;
    private String scenarioName;
    private double requestedCleanTwh;
    private double displacedFossilTwh;
    private double avoidedDirectMt;
    private double netAvoidedLifecycleMt;
    private double baselineCleanSharePct;
    private double simulatedCleanSharePct;
    private double cleanShareDeltaPct;

    // Target information being compared
    private boolean hasPowerSectorTarget;
    private String comparedTargetTitle;
    private String comparedTargetClassification; // OFFICIAL_NDC_COMMITMENT, JOINT_EU_NDC_COMMITMENT, DOMESTIC_POLICY_LAW
    private String comparedTargetCurrencyStatus;  // ACTIVE_LATEST_COMMITMENT, HISTORICAL_INTERMEDIATE_TARGET, STATUTORY_DOMESTIC

    // Explicitly separated metrics to prevent comparing capacity (GW) with generation (TWh)
    private Double targetPowerCleanGenerationSharePct; // Only populated when an actual generation target exists
    private Double targetPowerCapacitySharePct;        // Populated when target is installed capacity (GW)
    private Double cleanShareDistanceToTargetPct;       // Target - Simulated (strictly null when metric/scope is non-comparable)

    private String comparabilityStatus; // GENERATION_METRIC_COMPARABLE, NON_COMPARABLE_CAPACITY_VS_GENERATION, NON_COMPARABLE_ECONOMY_WIDE_GDP_INTENSITY, NON_COMPARABLE_ECONOMY_WIDE_NET_GHG
    private String alignmentStatus;     // Status string for UI badge display
    private String alignmentSummary;
    private String scopeMismatchExplanation;
    private List<String> methodologicalCaveats;
}
