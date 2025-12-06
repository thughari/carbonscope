package com.carbonscope.util;

import java.util.HashMap;
import java.util.Map;

public class ContinentUtil {

	private static final Map<String, String> COUNTRY_TO_CONTINENT = new HashMap<>();

	static {
		// Asia
		COUNTRY_TO_CONTINENT.put("IND", "Asia");
		COUNTRY_TO_CONTINENT.put("CHN", "Asia");
		COUNTRY_TO_CONTINENT.put("JPN", "Asia");
		COUNTRY_TO_CONTINENT.put("KOR", "Asia");
		COUNTRY_TO_CONTINENT.put("IDN", "Asia");
		COUNTRY_TO_CONTINENT.put("PAK", "Asia");
		COUNTRY_TO_CONTINENT.put("BGD", "Asia");
		COUNTRY_TO_CONTINENT.put("SAU", "Asia");
		COUNTRY_TO_CONTINENT.put("IRN", "Asia");
		COUNTRY_TO_CONTINENT.put("RUS", "Asia");

		// Europe
		COUNTRY_TO_CONTINENT.put("DEU", "Europe");
		COUNTRY_TO_CONTINENT.put("FRA", "Europe");
		COUNTRY_TO_CONTINENT.put("ESP", "Europe");
		COUNTRY_TO_CONTINENT.put("ITA", "Europe");
		COUNTRY_TO_CONTINENT.put("GBR", "Europe");
		COUNTRY_TO_CONTINENT.put("POL", "Europe");

		// North America
		COUNTRY_TO_CONTINENT.put("USA", "North America");
		COUNTRY_TO_CONTINENT.put("CAN", "North America");
		COUNTRY_TO_CONTINENT.put("MEX", "North America");

		// South America
		COUNTRY_TO_CONTINENT.put("BRA", "South America");
		COUNTRY_TO_CONTINENT.put("ARG", "South America");
		COUNTRY_TO_CONTINENT.put("BOL", "South America");
		COUNTRY_TO_CONTINENT.put("CHL", "South America");
		COUNTRY_TO_CONTINENT.put("COL", "South America");
		COUNTRY_TO_CONTINENT.put("PER", "South America");

		// Africa
		COUNTRY_TO_CONTINENT.put("ZAF", "Africa");
		COUNTRY_TO_CONTINENT.put("NGA", "Africa");
		COUNTRY_TO_CONTINENT.put("EGY", "Africa");
		COUNTRY_TO_CONTINENT.put("DZA", "Africa");
		COUNTRY_TO_CONTINENT.put("ETH", "Africa");
		COUNTRY_TO_CONTINENT.put("COD", "Africa");

		// Oceania
		COUNTRY_TO_CONTINENT.put("AUS", "Oceania");
		COUNTRY_TO_CONTINENT.put("NZL", "Oceania");
		// TODO add more countries and continents
	}

	public static String getContinent(String iso3) {
		if (iso3 == null) return null;
		return COUNTRY_TO_CONTINENT.getOrDefault(iso3.toUpperCase(), "Unknown");
	}
}
