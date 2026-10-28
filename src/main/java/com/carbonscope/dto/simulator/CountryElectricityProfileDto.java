package com.carbonscope.dto.simulator;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CountryElectricityProfileDto {
    private String countryCode;
    private String countryName;
    private double totalGenerationTwh;
    private double coalGenerationTwh;
    private double gasGenerationTwh;
    private double oilGenerationTwh;
    private double totalFossilGenerationTwh;
    private double cleanGenerationTwh;
    private double cleanPowerSharePct;
    private double powerSectorEmissionsMt;
    private double gridCarbonIntensityGco2PerKwh;

    // Provenance and boundary metadata
    private String dataSource;
    private String datasetUrl;
    private String sourceFile;
    private String sourceColumns;
    private String verificationStatus;
    private String publisher;
    private int baselineYear;
    private String accountingBoundary;
    private String metricDefinition;
    private String reconciliationStatus;
    private String generationAccountingConvention;
    private String cleanTechnologiesBreakdown;
    private String datasetSnapshotDate;
    private String methodologyUrl;
    private String sourcePublishedMetrics;
    private String derivedMetrics;
}
