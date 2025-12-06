package com.carbonscope.service;

import com.carbonscope.client.ClimateTraceClient;
import com.carbonscope.dto.*;
import com.carbonscope.model.EmissionSource;
import com.carbonscope.util.ContinentUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EmissionAnalyticsService {

	private final ClimateTraceClient client;

	private List<EmissionSource> filterByCountryAndContinent(List<EmissionSource> sources,
			String country,
			String continent) {
		return sources.stream()
				.filter(src -> {
					if (country != null && !country.isBlank()) {
						return country.equalsIgnoreCase(src.getCountry());
					}
					if (continent != null && !continent.isBlank()) {
						String c = ContinentUtil.getContinent(src.getCountry());
						return continent.equalsIgnoreCase(c); 
					}
					return true;
				})
				.collect(Collectors.toList());
	}

	public SectorSummaryResponse getSectorSummary(Integer year,
			String country,
			String continent,
			String gas,
			Integer limit) throws Exception {

		List<EmissionSource> sources = client.fetchSources(year, country, continent, null, null, gas, limit);

		List<EmissionSource> filtered = filterByCountryAndContinent(sources, country, continent);

		Map<String, Double> sectorTotals = filtered.stream()
				.collect(Collectors.groupingBy(
						EmissionSource::getSector,
						Collectors.summingDouble(EmissionSource::getEmissionsQuantity)
						));

		SectorSummaryResponse res = new SectorSummaryResponse();
		res.setSectorTotals(sectorTotals);
		res.setFilterCountry(country);
		res.setFilterContinent(continent);
		res.setGas(gas != null ? gas : "co2e_100yr");
		res.setYear(year != null ? year : 2024);
		return res;
	}

	public SubsectorSummaryResponse getSubsectorSummary(Integer year,
			String country,
			String continent,
			String sector,
			String gas,
			Integer limit) throws Exception {

		if (sector == null || sector.isBlank()) {
			throw new IllegalArgumentException("sector is required for subsector view");
		}

		List<EmissionSource> sources = client.fetchSources(year, country, continent, sector, null, gas, limit);
		List<EmissionSource> filtered = filterByCountryAndContinent(sources, country, continent);

		Map<String, Double> subsectorTotals = filtered.stream()
				.collect(Collectors.groupingBy(
						EmissionSource::getSubsector,
						Collectors.summingDouble(EmissionSource::getEmissionsQuantity)
						));

		SubsectorSummaryResponse res = new SubsectorSummaryResponse();
		res.setSector(sector);
		res.setSubsectorTotals(subsectorTotals);
		res.setFilterCountry(country);
		res.setFilterContinent(continent);
		res.setGas(gas != null ? gas : "co2e_100yr");
		res.setYear(year != null ? year : 2024);
		return res;
	}

	public CountrySummaryResponse getCountrySummary(Integer year,
			String continent,
			String sector,
			String gas,
			Integer limit) throws Exception {

		List<EmissionSource> sources = client.fetchSources(year, null, continent, sector, null, gas, limit);

		List<EmissionSource> filtered = sources.stream()
				.filter(src -> {
					if (continent != null && !continent.isBlank()) {
						String c = ContinentUtil.getContinent(src.getCountry());
						return continent.equalsIgnoreCase(c);
					}
					return true;
				})
				.collect(Collectors.toList());

		Map<String, Double> countryTotals = filtered.stream()
				.collect(Collectors.groupingBy(
						EmissionSource::getCountry,
						Collectors.summingDouble(EmissionSource::getEmissionsQuantity)
						));

		CountrySummaryResponse res = new CountrySummaryResponse();
		res.setCountryTotals(countryTotals);
		res.setFilterSector(sector);
		res.setFilterContinent(continent);
		res.setGas(gas != null ? gas : "co2e_100yr");
		res.setYear(year != null ? year : 2024);
		return res;
	}

	public ContinentSummaryResponse getContinentSummary(Integer year,
			String sector,
			String gas,
			Integer limit) throws Exception {

		List<EmissionSource> sources = client.fetchSources(year, null, null, sector, null, gas, limit);

		Map<String, Double> continentTotals = sources.stream()
				.collect(Collectors.groupingBy(
						src -> ContinentUtil.getContinent(src.getCountry()),
						Collectors.summingDouble(EmissionSource::getEmissionsQuantity)
						));

		continentTotals.remove("Unknown");

		ContinentSummaryResponse res = new ContinentSummaryResponse();
		res.setContinentTotals(continentTotals);
		res.setFilterSector(sector);
		res.setGas(gas != null ? gas : "co2e_100yr");
		res.setYear(year != null ? year : 2024);
		return res;
	}

	public SourcesResponse getSources(Integer year,
			String country,
			String continent,
			String sector,
			String subsector,
			String gas,
			Integer limit) throws Exception {

		List<EmissionSource> sources = client.fetchSources(year, country, continent, sector, subsector, gas, limit);
		List<EmissionSource> filtered = filterByCountryAndContinent(sources, country, continent);

		SourcesResponse res = new SourcesResponse();
		res.setSources(filtered);
		res.setFilterCountry(country);
		res.setFilterContinent(continent);
		res.setFilterSector(sector);
		res.setFilterSubsector(subsector);
		res.setGas(gas != null ? gas : "co2e_100yr");
		res.setYear(year != null ? year : 2024);
		return res;
	}
}