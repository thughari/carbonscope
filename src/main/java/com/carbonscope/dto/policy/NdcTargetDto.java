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
public class NdcTargetDto {
    private String id;
    private String targetTitle;
    private String targetType; // ABSOLUTE_REDUCTION, INTENSITY_GDP, RENEWABLE_SHARE_CAPACITY, RENEWABLE_SHARE_GENERATION, FIXED_EMISSIONS_RANGE, DEVIATION_FROM_BAU

    /**
     * Provenance classification:
     * - OFFICIAL_NDC_COMMITMENT: Explicit national commitment in Party's official NDC submitted to UNFCCC
     * - JOINT_EU_NDC_COMMITMENT: EU-wide joint fulfilment NDC commitment covering all 27 member states
     * - DOMESTIC_POLICY_LAW: Domestic statutory act, national decree, or ministerial roadmap (not in UNFCCC NDC)
     * - OTHER_OFFICIAL_SOURCE: Official multilateral partnership (e.g. JETP declaration)
     * - REQUIRES_REVIEW: Documented commitment subject to policy divergence or withdrawal review
     */
    private String targetClassification;

    /**
     * Target currency / lifecycle status:
     * - ACTIVE_LATEST_COMMITMENT: Primary active commitment in the latest valid submission (e.g. 2035 NDC)
     * - HISTORICAL_INTERMEDIATE_TARGET: Active or milestone target for 2030 from prior/reaffirmed submissions
     * - STATUTORY_DOMESTIC: Ongoing domestic law/regulation target
     * - REQUIRES_REVIEW: Under official review / policy shift
     */
    private String targetCurrencyStatus;

    private Integer targetYear;
    private Integer baselineYear; // null if BAU or fixed level
    private String commitmentStatus; // UNCONDITIONAL, CONDITIONAL, COMBINED
    private Double targetValue;
    private Double targetValueConditional;
    private String targetRange;
    private String targetUnit; // PERCENT, MT_CO2E, GW
    private String sectorScope; // ECONOMY_WIDE, POWER_SECTOR, ENERGY, LULUCF
    private List<String> gasesCovered; // CO2, CH4, N2O, etc.
    private String officialDescription;
    private String powerSectorImplication;
    private List<String> interpretationCaveats;
    private String legalBasisCitation;
    private String verificationDate;
}
