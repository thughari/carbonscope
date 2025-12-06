package com.carbonscope.dto;

import lombok.Data;

import java.util.Map;

@Data
public class ContinentSummaryResponse {
    private Map<String, Double> continentTotals;

    private String filterSector;
    private String gas;
    private int year;
}
