package com.carbonscope.dto;

import lombok.Data;

import java.util.Map;

@Data
public class SectorSummaryResponse {
	private Map<String, Double> sectorTotals;

	private String filterCountry;
	private String filterContinent; 
	private String gas;
	private int year;
}
