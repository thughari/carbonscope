package com.carbonscope.dto.simulator;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecarbonizationBaselineDto {
    private double totalGenerationTwh;
    private double fossilGenerationTwh;
    private double targetFuelGenerationTwh;
    private double cleanGenerationTwh;
    private double cleanPowerSharePct;
    private double powerSectorEmissionsMt;
    private double gridCarbonIntensityGco2PerKwh;
    private double nationalTotalEmissionsMt;
    private String sourceName;
    private String datasetUrl;
    private String sourceFile;
    private String sourceColumns;
    private String verificationStatus;
    private int reportingYear;
    private String accountingBoundary;
    private String metricDefinition;
    private String generationAccountingConvention;
    private String reconciliationStatus;
    private String cleanTechnologiesIncluded;
    private String cleanTechnologiesBreakdown;
    private String datasetSnapshotDate;
    private String methodologyUrl;
    private String sourcePublishedMetrics;
    private String derivedMetrics;
}
