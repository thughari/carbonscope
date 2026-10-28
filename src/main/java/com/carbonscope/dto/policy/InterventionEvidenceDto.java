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
public class InterventionEvidenceDto {
    private String technologyId;
    private String displayName;
    private String category; // CLEAN_GENERATION or FOSSIL_DISPLACEMENT
    private double directFactorGco2PerKwh;
    private double lifecycleFactorMedianGco2PerKwh;
    private double lifecycleFactorMinGco2PerKwh;
    private double lifecycleFactorMaxGco2PerKwh;
    private String emissionFactorUnit;
    private String sourceCitation;
    private int publicationYear;
    private String accountingBoundary;
    private String technologyScope;
    private String geographicApplicability;
    private String dataQualityRating;
    private boolean isProvisional;
    private String provisionalWarning;
    private List<String> physicalLimitations;

    // Explicitly documented unavailable fields to prevent fabrication
    private boolean lcoeDataAvailable;
    private String lcoeUnavailableReason;
    private boolean capacityFactorDataAvailable;
    private String capacityFactorUnavailableReason;
}
