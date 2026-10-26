package com.carbonscope.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmissionsOverviewResponse {
    private String country;
    private String countryName;
    private String continent;
    private int year;
    private String gas;
    private double totalEmissionsTonnes;
    private double totalEmissionsMt;
    private double worldEmissionsMt;
    private Integer countryRank;
    private double percentageOfGlobal;
    private double perCapitaTonnes;
    private Double gridCarbonIntensityGco2PerKwh;
    private Double cleanPowerPercentage;
    private Map<String, Double> gasBreakdownMt;
    private Map<String, Double> sectorTotalsMt;
    private List<TrendPointDto> historicalTrend;
    private List<SourceInfoItem> verifiedSources;
    private String dataReliability;
    private String dataSourceStatus;
    private long responseTimeMs;
}
