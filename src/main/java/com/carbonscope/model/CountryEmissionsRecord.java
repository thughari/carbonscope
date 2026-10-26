package com.carbonscope.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CountryEmissionsRecord {
    private String country;
    private String continent;
    private Integer rank;
    private Integer previousRank;
    private Map<String, Double> emissions;
    private Map<String, Double> worldEmissions;
    private Map<String, Double> emissionsChange;
}
