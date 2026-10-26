package com.carbonscope.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AssetSectorRecord {

    @JsonProperty("Sector")
    private String sector;

    @JsonProperty("Country")
    private String country;

    @JsonProperty("Gas")
    private String gas;

    @JsonProperty("Emissions")
    private double emissions;

    @JsonProperty("AssetCount")
    private int assetCount;
}
