package com.carbonscope.dto;

import lombok.Data;

import java.util.Map;

@Data
public class SubsectorSummaryResponse {
	private String sector;
	private Map<String, Double> subsectorTotals;

	private String filterCountry;
	private String filterContinent;
	private String gas;
	private int year;
}
