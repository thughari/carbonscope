package com.carbonscope.model;

import lombok.Data;

@Data
public class EmissionSource {
	private long id;
	private String name;
	private String sector;
	private String subsector;
	private String country;
	private String assetType;
	private String sourceType;
	private String gas;
	private double emissionsQuantity;
	private int year;
}
