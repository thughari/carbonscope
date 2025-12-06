package com.carbonscope.dto;

import com.carbonscope.model.EmissionSource;
import lombok.Data;

import java.util.List;

@Data
public class SourcesResponse {
    private List<EmissionSource> sources;
    private String filterCountry;
    private String filterContinent;
    private String filterSector;
    private String filterSubsector;
    private String gas;
    private int year;
}
