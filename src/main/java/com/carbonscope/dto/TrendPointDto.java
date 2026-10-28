package com.carbonscope.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrendPointDto {
    private int year;
    private double emissionsMt;
    private double worldEmissionsMt;
    private double shareOfGlobal;
    private String provenance;
    private boolean available;
    private boolean isProvisional;
    private String temporalCompleteness;

    public TrendPointDto(int year, double emissionsMt, double worldEmissionsMt, double shareOfGlobal) {
        this.year = year;
        this.emissionsMt = emissionsMt;
        this.worldEmissionsMt = worldEmissionsMt;
        this.shareOfGlobal = shareOfGlobal;
        this.available = (emissionsMt > 0);
        this.provenance = (emissionsMt > 0) ? "LIVE_API_OBSERVATION" : "DATA_UNAVAILABLE";
        this.isProvisional = (year >= 2025);
        this.temporalCompleteness = (year <= 2024) ? "CONSOLIDATED_ANNUAL" : (year == 2025 ? "PROVISIONAL_ANNUAL" : "PARTIAL_YEAR_OBSERVATION");
    }
}
