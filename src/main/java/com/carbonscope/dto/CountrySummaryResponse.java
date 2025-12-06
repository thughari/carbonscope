package com.carbonscope.dto;

import lombok.Data;

import java.util.Map;

@Data
public class CountrySummaryResponse {
    private Map<String, Double> countryTotals;

    private String filterSector;
    private String filterContinent;
    private String gas;
    private int year;
}
