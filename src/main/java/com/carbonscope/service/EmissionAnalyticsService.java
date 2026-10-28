package com.carbonscope.service;

import com.carbonscope.client.ClimateTraceClient;
import com.carbonscope.dto.*;
import com.carbonscope.model.AssetSectorRecord;
import com.carbonscope.model.CountryEmissionsRecord;
import com.carbonscope.model.EmissionSource;
import com.carbonscope.util.ContinentUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmissionAnalyticsService {

    private final ClimateTraceClient client;
    private final ReliableCarbonSourcesService reliableSources;
    private final EmissionCacheService cacheService;

    private static final List<String> TOP_EMITTER_CODES = List.of(
            "CHN", "USA", "IND", "RUS", "IDN", "BRA", "JPN", "IRN", "SAU", "DEU",
            "CAN", "KOR", "MEX", "ZAF", "AUS", "TUR", "GBR", "POL", "ITA", "FRA"
    );

    public EmissionsOverviewResponse getEmissionsOverview(Integer year,
                                                          String country,
                                                          String continent,
                                                          String gas) {
        long start = System.currentTimeMillis();
        int finalYear = ClimateTraceClient.resolveYear(year);
        String finalGas = (gas != null && !gas.isBlank()) ? gas : "co2e_100yr";
        String countryCode = (country != null && !country.isBlank()) ? country.toUpperCase() : null;

        String cacheKey = "overview_" + finalYear + "_" + countryCode + "_" + continent + "_" + finalGas;
        EmissionsOverviewResponse cached = cacheService.get(cacheKey);
        if (cached != null) {
            cached.setResponseTimeMs(System.currentTimeMillis() - start);
            return cached;
        }

        // 1. Validate temporal boundary
        if (year != null && !ClimateTraceClient.isSupportedYear(year)) {
            String countryName = reliableSources.getCountryName(countryCode);
            String notes = String.format("Requested year %d is outside empirical satellite observation coverage (%d-%d). No synthetic values generated.",
                    year, ClimateTraceClient.EARLIEST_YEAR, ClimateTraceClient.LATEST_VERIFIED_YEAR);

            ProvenanceDto provenance = ProvenanceDto.builder()
                    .status("DATA_UNAVAILABLE")
                    .reportingYear(year)
                    .temporalCompleteness("UNAVAILABLE")
                    .isPartialOrProvisional(false)
                    .empirical(false)
                    .qualityNotes(notes)
                    .build();

            EmissionsOverviewResponse unavailableResponse = EmissionsOverviewResponse.builder()
                    .country(countryCode)
                    .countryName(countryName)
                    .continent(continent != null ? continent : ContinentUtil.getContinent(countryCode))
                    .year(year)
                    .gas(finalGas)
                    .totalEmissionsTonnes(0.0)
                    .totalEmissionsMt(0.0)
                    .worldEmissionsMt(0.0)
                    .countryRank(null)
                    .percentageOfGlobal(0.0)
                    .perCapitaTonnes(null)
                    .gridCarbonIntensityGco2PerKwh(null)
                    .cleanPowerPercentage(null)
                    .gasBreakdownMt(Collections.emptyMap())
                    .sectorTotalsMt(Collections.emptyMap())
                    .historicalTrend(buildHistoricalTrend(countryCode, finalGas))
                    .verifiedSources(reliableSources.getAllVerifiedSources())
                    .dataReliability("Data Unavailable")
                    .dataSourceStatus("DATA_UNAVAILABLE")
                    .temporalCompleteness("UNAVAILABLE")
                    .isPartialOrProvisional(false)
                    .provenance(provenance)
                    .dataQualityNotes(notes)
                    .responseTimeMs(System.currentTimeMillis() - start)
                    .build();

            cacheService.put(cacheKey, unavailableResponse);
            return unavailableResponse;
        }

        // 2. Fetch live satellite records
        CountryEmissionsRecord countryRecord = null;
        CountryEmissionsRecord worldRecord = null;
        String status = "LIVE_API_OBSERVATION";

        // Determine provisional / partial year status
        String temporalCompleteness = (finalYear <= 2024)
                ? "CONSOLIDATED_ANNUAL"
                : (finalYear == 2025 ? "PROVISIONAL_ANNUAL" : "PARTIAL_YEAR_OBSERVATION");
        boolean isPartialOrProvisional = (finalYear >= 2025);

        String qualityNotes = (finalYear == 2026)
                ? "Provisional year-to-date satellite observations (~7 months elapsed), not an annualized complete accounting total."
                : (finalYear == 2025
                    ? "Preliminary 2025 annual estimate pending final intergovernmental validation."
                    : "Verified 12-month consolidated empirical satellite inventory (Climate TRACE v6).");

        try {
            if (countryCode != null) {
                countryRecord = client.fetchCountryEmissions(finalYear, countryCode);
            }
            worldRecord = client.fetchCountryEmissions(finalYear, null);
        } catch (Exception e) {
            log.warn("Error fetching emissions record from external API: {}", e.getMessage());
        }

        double totalEmissionsTonnes = 0;
        double worldEmissionsMt = 57582.0;
        Integer rank = null;

        if (worldRecord != null && worldRecord.getEmissions() != null) {
            Double wVal = worldRecord.getEmissions().get(finalGas);
            if (wVal != null && wVal > 0) {
                worldEmissionsMt = wVal / 1_000_000.0;
            }
        }

        Map<String, Double> gasBreakdownMt = new LinkedHashMap<>();

        if (countryCode != null) {
            if (countryRecord != null && countryRecord.getEmissions() != null && !countryRecord.getEmissions().isEmpty()) {
                Map<String, Double> em = countryRecord.getEmissions();
                totalEmissionsTonnes = em.getOrDefault(finalGas, em.getOrDefault("co2e_100yr", 0.0));
                rank = countryRecord.getRank();
                em.forEach((k, v) -> gasBreakdownMt.put(k, v / 1_000_000.0));
            } else {
                // Check if reference benchmark exists for calibrated baseline year (2024)
                Double baselineMt = reliableSources.getBaselineTotalMt(countryCode);
                if (finalYear == ClimateTraceClient.DEFAULT_YEAR && baselineMt != null) {
                    status = "STATIC_REFERENCE_BENCHMARK";
                    totalEmissionsTonnes = baselineMt * 1_000_000.0;
                    rank = reliableSources.getBaselineRank(countryCode);
                    gasBreakdownMt.put(finalGas, totalEmissionsTonnes / 1_000_000.0);
                    qualityNotes = "Live satellite API temporarily unavailable; serving calibrated 2024 consensus benchmark (Ember/GCP).";
                    temporalCompleteness = "CONSOLIDATED_ANNUAL";
                    isPartialOrProvisional = false;
                } else {
                    status = "DATA_UNAVAILABLE";
                    totalEmissionsTonnes = 0.0;
                    qualityNotes = String.format("No empirical observations or verified benchmarks found for %s in %d.",
                            countryCode, finalYear);
                    temporalCompleteness = "UNAVAILABLE";
                    isPartialOrProvisional = false;
                }
            }
        } else {
            // Global view
            if (worldRecord != null && worldRecord.getEmissions() != null && !worldRecord.getEmissions().isEmpty()) {
                totalEmissionsTonnes = worldEmissionsMt * 1_000_000.0;
                worldRecord.getEmissions().forEach((k, v) -> gasBreakdownMt.put(k, v / 1_000_000.0));
            } else {
                if (finalYear == ClimateTraceClient.DEFAULT_YEAR) {
                    status = "STATIC_REFERENCE_BENCHMARK";
                    totalEmissionsTonnes = worldEmissionsMt * 1_000_000.0;
                    qualityNotes = "Live API unavailable; serving global reference consensus total (57,582 Mt CO2e).";
                    temporalCompleteness = "CONSOLIDATED_ANNUAL";
                    isPartialOrProvisional = false;
                } else {
                    status = "DATA_UNAVAILABLE";
                    totalEmissionsTonnes = 0.0;
                    worldEmissionsMt = 0.0;
                    qualityNotes = "No empirical observations available for Global in year " + finalYear + ".";
                    temporalCompleteness = "UNAVAILABLE";
                    isPartialOrProvisional = false;
                }
            }
        }

        double totalEmissionsMt = totalEmissionsTonnes / 1_000_000.0;
        double percentageOfGlobal = (worldEmissionsMt > 0 && totalEmissionsMt > 0)
                ? (totalEmissionsMt / worldEmissionsMt) * 100.0
                : 0.0;
        if (countryCode == null && totalEmissionsMt > 0) {
            percentageOfGlobal = 100.0;
        }

        // 3. Fetch empirical sector summary
        Map<String, Double> sectorTotalsMt = new LinkedHashMap<>();
        try {
            SectorSummaryResponse sectorRes = getSectorSummary(finalYear, countryCode, continent, finalGas, 4000);
            if (sectorRes != null && sectorRes.getSectorTotals() != null) {
                sectorRes.getSectorTotals().forEach((k, v) -> sectorTotalsMt.put(k, v / 1_000_000.0));
            }
        } catch (Exception e) {
            log.warn("Error getting sector summary for overview: {}", e.getMessage());
        }

        // 4. Multi-year empirical trajectory
        List<TrendPointDto> trend = buildHistoricalTrend(countryCode, finalGas);

        String countryName = reliableSources.getCountryName(countryCode);
        Double perCapita = "DATA_UNAVAILABLE".equals(status) ? null : reliableSources.getPerCapitaEmissions(countryCode);
        Double gridIntensity = "DATA_UNAVAILABLE".equals(status) ? null : reliableSources.getGridCarbonIntensity(countryCode);
        Double cleanPower = "DATA_UNAVAILABLE".equals(status) ? null : reliableSources.getCleanPowerShare(countryCode);

        // 5. Scientific data reliability label
        String dataReliability;
        boolean isEmpirical = "LIVE_API_OBSERVATION".equals(status);
        if ("LIVE_API_OBSERVATION".equals(status)) {
            dataReliability = isPartialOrProvisional
                    ? (finalYear == 2026 ? "Provisional Satellite Observation (Partial Year YTD)" : "Provisional Annual Observation (Climate TRACE)")
                    : "Empirical Satellite Inventory (Climate TRACE v6)";
        } else if ("STATIC_REFERENCE_BENCHMARK".equals(status)) {
            dataReliability = "Calibrated Reference Benchmark (Ember / GCP Consensus)";
        } else {
            dataReliability = "Data Unavailable";
        }

        ProvenanceDto provenance = ProvenanceDto.builder()
                .status(status)
                .sourceName(isEmpirical ? "Climate TRACE v6 Satellite & AI Observation" : "Global Carbon Project & Ember Climate")
                .methodology(isEmpirical ? "Direct multi-spectral satellite sensors and AI facility tracking" : "Peer-reviewed national energy balance and emission consensus")
                .reportingYear(finalYear)
                .temporalCompleteness(temporalCompleteness)
                .isPartialOrProvisional(isPartialOrProvisional)
                .empirical(isEmpirical)
                .qualityNotes(qualityNotes)
                .build();

        EmissionsOverviewResponse response = EmissionsOverviewResponse.builder()
                .country(countryCode)
                .countryName(countryName)
                .continent(continent != null ? continent : ContinentUtil.getContinent(countryCode))
                .year(finalYear)
                .gas(finalGas)
                .totalEmissionsTonnes(totalEmissionsTonnes)
                .totalEmissionsMt(totalEmissionsMt)
                .worldEmissionsMt(worldEmissionsMt)
                .countryRank(rank)
                .percentageOfGlobal(percentageOfGlobal)
                .perCapitaTonnes(perCapita)
                .gridCarbonIntensityGco2PerKwh(gridIntensity)
                .cleanPowerPercentage(cleanPower)
                .gasBreakdownMt(gasBreakdownMt)
                .sectorTotalsMt(sectorTotalsMt)
                .historicalTrend(trend)
                .verifiedSources(reliableSources.getAllVerifiedSources())
                .dataReliability(dataReliability)
                .dataSourceStatus(status)
                .temporalCompleteness(temporalCompleteness)
                .isPartialOrProvisional(isPartialOrProvisional)
                .provenance(provenance)
                .dataQualityNotes(qualityNotes)
                .responseTimeMs(System.currentTimeMillis() - start)
                .build();

        cacheService.put(cacheKey, response);
        return response;
    }

    private List<TrendPointDto> buildHistoricalTrend(String countryCode, String gas) {
        String cacheKey = "trend_" + countryCode + "_" + gas;
        List<TrendPointDto> cached = cacheService.get(cacheKey);
        if (cached != null) return cached;

        int startYear = ClimateTraceClient.EARLIEST_YEAR;
        int endYear = ClimateTraceClient.LATEST_VERIFIED_YEAR;
        List<Integer> yearList = IntStream.rangeClosed(startYear, endYear).boxed().toList();

        List<CompletableFuture<TrendPointDto>> futures = yearList.stream()
                .map(y -> CompletableFuture.supplyAsync(() -> fetchSingleYearTrendPoint(y, countryCode, gas)))
                .toList();

        List<TrendPointDto> list = futures.stream()
                .map(CompletableFuture::join)
                .sorted(Comparator.comparingInt(TrendPointDto::getYear))
                .collect(Collectors.toList());

        cacheService.put(cacheKey, list);
        return list;
    }

    private TrendPointDto fetchSingleYearTrendPoint(int y, String countryCode, String gas) {
        double cMt = 0;
        double wMt = 0;
        try {
            if (countryCode != null) {
                CountryEmissionsRecord rec = client.fetchCountryEmissions(y, countryCode);
                if (rec != null && rec.getEmissions() != null) {
                    cMt = rec.getEmissions().getOrDefault(gas, rec.getEmissions().getOrDefault("co2e_100yr", 0.0)) / 1_000_000.0;
                }
                if (rec != null && rec.getWorldEmissions() != null) {
                    wMt = rec.getWorldEmissions().getOrDefault(gas, rec.getWorldEmissions().getOrDefault("co2e_100yr", 0.0)) / 1_000_000.0;
                }
            } else {
                CountryEmissionsRecord rec = client.fetchCountryEmissions(y, null);
                if (rec != null && rec.getEmissions() != null) {
                    cMt = rec.getEmissions().getOrDefault(gas, rec.getEmissions().getOrDefault("co2e_100yr", 0.0)) / 1_000_000.0;
                    wMt = cMt;
                }
            }
        } catch (Exception e) {
            log.debug("Trend year fetch failed for {}: {}", y, e.getMessage());
        }

        boolean isProv = (y >= 2025);
        String tempComp = (y <= 2024) ? "CONSOLIDATED_ANNUAL" : (y == 2025 ? "PROVISIONAL_ANNUAL" : "PARTIAL_YEAR_OBSERVATION");

        // No synthetic growth extrapolation (0.015 factor removed).
        if (cMt <= 0) {
            return TrendPointDto.builder()
                    .year(y)
                    .emissionsMt(0.0)
                    .worldEmissionsMt(wMt)
                    .shareOfGlobal(0.0)
                    .provenance("DATA_UNAVAILABLE")
                    .available(false)
                    .isProvisional(isProv)
                    .temporalCompleteness(tempComp)
                    .build();
        }

        double share = (wMt > 0) ? (cMt / wMt) * 100.0 : 0.0;
        return TrendPointDto.builder()
                .year(y)
                .emissionsMt(cMt)
                .worldEmissionsMt(wMt)
                .shareOfGlobal(share)
                .provenance("LIVE_API_OBSERVATION")
                .available(true)
                .isProvisional(isProv)
                .temporalCompleteness(tempComp)
                .build();
    }

    public SectorSummaryResponse getSectorSummary(Integer year,
                                                  String country,
                                                  String continent,
                                                  String gas,
                                                  Integer limit) throws Exception {

        int finalYear = ClimateTraceClient.resolveYear(year);
        String finalGas = (gas != null && !gas.isBlank()) ? gas : "co2e_100yr";
        String countryCode = (country != null && !country.isBlank()) ? country.toUpperCase() : null;

        String cacheKey = "sectors_" + finalYear + "_" + countryCode + "_" + continent + "_" + finalGas;
        SectorSummaryResponse cached = cacheService.get(cacheKey);
        if (cached != null) return cached;

        Map<String, Double> sectorTotals = new LinkedHashMap<>();

        // 1. Try nationwide / global asset sector breakdown
        List<AssetSectorRecord> assetRecords = client.fetchAssetSectorEmissions(finalYear, countryCode, finalGas);
        if (assetRecords != null && !assetRecords.isEmpty()) {
            for (AssetSectorRecord rec : assetRecords) {
                String standardSector = mapToStandardSector(rec.getSector());
                sectorTotals.merge(standardSector, rec.getEmissions(), Double::sum);
            }
        }

        // 2. If asset sectors was empty, fall back to point sources
        if (sectorTotals.isEmpty()) {
            List<EmissionSource> sources = client.fetchSources(finalYear, countryCode, continent, null, null, finalGas, limit);
            if (sources != null && !sources.isEmpty()) {
                List<EmissionSource> filtered = filterByCountryAndContinent(sources, countryCode, continent);
                for (EmissionSource src : filtered) {
                    if (src.getSector() != null && src.getEmissionsQuantity() > 0) {
                        String standardSector = mapToStandardSector(src.getSector());
                        sectorTotals.merge(standardSector, src.getEmissionsQuantity(), Double::sum);
                    }
                }
            }
        }

        // No synthetic sector distributions. Real observations or honest DATA_UNAVAILABLE.
        boolean isAvailable = !sectorTotals.isEmpty();

        Map<String, Double> sorted = sectorTotals.entrySet().stream()
                .filter(e -> e.getValue() != null && e.getValue() > 0)
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));

        String temporalCompleteness = (finalYear <= 2024)
                ? "CONSOLIDATED_ANNUAL"
                : (finalYear == 2025 ? "PROVISIONAL_ANNUAL" : "PARTIAL_YEAR_OBSERVATION");

        ProvenanceDto provenance = ProvenanceDto.builder()
                .status(isAvailable ? "LIVE_API_OBSERVATION" : "DATA_UNAVAILABLE")
                .sourceName(isAvailable ? "Climate TRACE v6 Asset Inventories" : "None")
                .reportingYear(finalYear)
                .temporalCompleteness(isAvailable ? temporalCompleteness : "UNAVAILABLE")
                .isPartialOrProvisional(finalYear >= 2025)
                .empirical(isAvailable)
                .qualityNotes(isAvailable
                        ? "Sector totals aggregated from verified satellite asset observations."
                        : "No empirical sector asset records found for the requested criteria.")
                .build();

        SectorSummaryResponse res = new SectorSummaryResponse();
        res.setSectorTotals(sorted);
        res.setFilterCountry(countryCode);
        res.setFilterContinent(continent);
        res.setGas(finalGas);
        res.setYear(finalYear);
        res.setDataSourceStatus(isAvailable ? "LIVE_API_OBSERVATION" : "DATA_UNAVAILABLE");
        res.setProvenance(provenance);

        cacheService.put(cacheKey, res);
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

        int finalYear = ClimateTraceClient.resolveYear(year);
        String finalGas = (gas != null && !gas.isBlank()) ? gas : "co2e_100yr";
        String countryCode = (country != null && !country.isBlank()) ? country.toUpperCase() : null;

        String cacheKey = "subsectors_" + finalYear + "_" + countryCode + "_" + continent + "_" + sector + "_" + finalGas;
        SubsectorSummaryResponse cached = cacheService.get(cacheKey);
        if (cached != null) return cached;

        Map<String, Double> subsectorTotals = new LinkedHashMap<>();

        // 1. Try asset records
        if (countryCode != null) {
            List<AssetSectorRecord> assetRecords = client.fetchAssetSectorEmissions(finalYear, countryCode, finalGas);
            if (assetRecords != null) {
                for (AssetSectorRecord rec : assetRecords) {
                    if (mapToStandardSector(rec.getSector()).equalsIgnoreCase(sector)) {
                        subsectorTotals.merge(rec.getSector(), rec.getEmissions(), Double::sum);
                    }
                }
            }
        }

        // 2. If empty, try sources API
        if (subsectorTotals.isEmpty()) {
            List<EmissionSource> sources = client.fetchSources(finalYear, countryCode, continent, sector, null, finalGas, limit);
            if (sources != null && !sources.isEmpty()) {
                List<EmissionSource> filtered = filterByCountryAndContinent(sources, countryCode, continent);
                for (EmissionSource src : filtered) {
                    if (src.getSubsector() != null && src.getEmissionsQuantity() > 0) {
                        subsectorTotals.merge(src.getSubsector(), src.getEmissionsQuantity(), Double::sum);
                    }
                }
            }
        }

        boolean isAvailable = !subsectorTotals.isEmpty();

        Map<String, Double> sorted = subsectorTotals.entrySet().stream()
                .filter(e -> e.getValue() != null && e.getValue() > 0)
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));

        ProvenanceDto provenance = ProvenanceDto.builder()
                .status(isAvailable ? "LIVE_API_OBSERVATION" : "DATA_UNAVAILABLE")
                .sourceName(isAvailable ? "Climate TRACE v6 Asset Inventories" : "None")
                .reportingYear(finalYear)
                .temporalCompleteness(isAvailable ? (finalYear <= 2024 ? "CONSOLIDATED_ANNUAL" : "PROVISIONAL_OBSERVATION") : "UNAVAILABLE")
                .isPartialOrProvisional(finalYear >= 2025)
                .empirical(isAvailable)
                .qualityNotes(isAvailable
                        ? "Granular subsector asset totals from Climate TRACE observations."
                        : "No empirical subsector observations available for " + sector + ".")
                .build();

        SubsectorSummaryResponse res = new SubsectorSummaryResponse();
        res.setSector(sector);
        res.setSubsectorTotals(sorted);
        res.setFilterCountry(countryCode);
        res.setFilterContinent(continent);
        res.setGas(finalGas);
        res.setYear(finalYear);
        res.setDataSourceStatus(isAvailable ? "LIVE_API_OBSERVATION" : "DATA_UNAVAILABLE");
        res.setProvenance(provenance);

        cacheService.put(cacheKey, res);
        return res;
    }

    public CountrySummaryResponse getCountrySummary(Integer year,
                                                    String continent,
                                                    String sector,
                                                    String gas,
                                                    Integer limit) throws Exception {

        int finalYear = ClimateTraceClient.resolveYear(year);
        String finalGas = (gas != null && !gas.isBlank()) ? gas : "co2e_100yr";

        String cacheKey = "countries_" + finalYear + "_" + continent + "_" + sector + "_" + finalGas;
        CountrySummaryResponse cached = cacheService.get(cacheKey);
        if (cached != null) return cached;

        Map<String, Double> countryTotals = new LinkedHashMap<>();

        // Populate verified reference totals for top emitting countries (2024 calibrated benchmark)
        for (String code : TOP_EMITTER_CODES) {
            if (continent != null && !continent.isBlank()) {
                String c = ContinentUtil.getContinent(code);
                if (!continent.equalsIgnoreCase(c)) continue;
            }

            Double baselineMt = reliableSources.getBaselineTotalMt(code);
            if (baselineMt != null) {
                countryTotals.put(code, baselineMt * 1_000_000.0);
            }
        }

        ProvenanceDto provenance = ProvenanceDto.builder()
                .status("STATIC_REFERENCE_BENCHMARK")
                .sourceName("Global Carbon Project & Climate TRACE 2024 Consensus")
                .methodology("Calibrated top emitter national baseline totals")
                .reportingYear(2024)
                .empirical(false)
                .temporalCompleteness("CONSOLIDATED_ANNUAL")
                .isPartialOrProvisional(false)
                .qualityNotes("Calibrated 2024 reference baselines for top 20 emitter nations.")
                .build();

        CountrySummaryResponse res = new CountrySummaryResponse();
        res.setCountryTotals(countryTotals);
        res.setFilterSector(sector);
        res.setFilterContinent(continent);
        res.setGas(finalGas);
        res.setYear(finalYear);
        res.setDataSourceStatus("STATIC_REFERENCE_BENCHMARK");
        res.setProvenance(provenance);

        cacheService.put(cacheKey, res);
        return res;
    }

    public ContinentSummaryResponse getContinentSummary(Integer year,
                                                        String sector,
                                                        String gas,
                                                        Integer limit) throws Exception {

        int finalYear = ClimateTraceClient.resolveYear(year);
        String finalGas = (gas != null && !gas.isBlank()) ? gas : "co2e_100yr";

        String cacheKey = "continents_" + finalYear + "_" + sector + "_" + finalGas;
        ContinentSummaryResponse cached = cacheService.get(cacheKey);
        if (cached != null) return cached;

        Map<String, Double> continentTotals = new LinkedHashMap<>();
        for (String code : TOP_EMITTER_CODES) {
            String cont = ContinentUtil.getContinent(code);
            if (cont != null && !"Unknown".equalsIgnoreCase(cont)) {
                Double baselineMt = reliableSources.getBaselineTotalMt(code);
                if (baselineMt != null) {
                    continentTotals.merge(cont, baselineMt * 1_000_000.0, Double::sum);
                }
            }
        }

        ProvenanceDto provenance = ProvenanceDto.builder()
                .status("STATIC_REFERENCE_BENCHMARK")
                .sourceName("Global Carbon Project & Climate TRACE 2024 Consensus")
                .methodology("Aggregated continental baselines from calibrated national totals")
                .reportingYear(2024)
                .empirical(false)
                .temporalCompleteness("CONSOLIDATED_ANNUAL")
                .isPartialOrProvisional(false)
                .qualityNotes("Aggregated 2024 continental reference baselines.")
                .build();

        ContinentSummaryResponse res = new ContinentSummaryResponse();
        res.setContinentTotals(continentTotals);
        res.setFilterSector(sector);
        res.setGas(finalGas);
        res.setYear(finalYear);
        res.setDataSourceStatus("STATIC_REFERENCE_BENCHMARK");
        res.setProvenance(provenance);

        cacheService.put(cacheKey, res);
        return res;
    }

    public SourcesResponse getSources(Integer year,
                                      String country,
                                      String continent,
                                      String sector,
                                      String subsector,
                                      String gas,
                                      Integer limit) throws Exception {

        int finalYear = ClimateTraceClient.resolveYear(year);
        List<EmissionSource> sources = client.fetchSources(year, country, continent, sector, subsector, gas, limit);
        if (sources == null) sources = Collections.emptyList();

        List<EmissionSource> filtered = filterByCountryAndContinent(sources, country, continent);
        boolean isAvailable = !filtered.isEmpty();

        String temporalCompleteness = (finalYear <= 2024)
                ? "CONSOLIDATED_ANNUAL"
                : (finalYear == 2025 ? "PROVISIONAL_ANNUAL" : "PARTIAL_YEAR_OBSERVATION");

        ProvenanceDto provenance = ProvenanceDto.builder()
                .status(isAvailable ? "LIVE_API_OBSERVATION" : "DATA_UNAVAILABLE")
                .sourceName(isAvailable ? "Climate TRACE v7 Point-Source Database" : "None")
                .methodology("Satellite remote sensing and asset-level facility attribution")
                .reportingYear(finalYear)
                .empirical(isAvailable)
                .temporalCompleteness(isAvailable ? temporalCompleteness : "UNAVAILABLE")
                .isPartialOrProvisional(finalYear >= 2025)
                .qualityNotes(isAvailable
                        ? "Point-source facility observations."
                        : "No point-source assets found for specified criteria.")
                .build();

        SourcesResponse res = new SourcesResponse();
        res.setSources(filtered);
        res.setFilterCountry(country);
        res.setFilterContinent(continent);
        res.setFilterSector(sector);
        res.setFilterSubsector(subsector);
        res.setGas(gas != null ? gas : "co2e_100yr");
        res.setYear(finalYear);
        res.setDataSourceStatus(isAvailable ? "LIVE_API_OBSERVATION" : "DATA_UNAVAILABLE");
        res.setProvenance(provenance);
        return res;
    }

    private List<EmissionSource> filterByCountryAndContinent(List<EmissionSource> sources,
                                                            String country,
                                                            String continent) {
        if (sources == null || sources.isEmpty()) {
            return Collections.emptyList();
        }

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

    private String mapToStandardSector(String rawSector) {
        if (rawSector == null) return "other";
        String s = rawSector.toLowerCase();

        // 1. Power & Energy
        if (s.contains("electric") || s.contains("power") || s.contains("heat-plant")
                || s.contains("energy-use") || s.contains("solid-fuel") || s.contains("reservoir")) {
            return "power";
        }

        // 2. Heavy & Light Industry / Manufacturing
        if (s.contains("steel") || s.contains("cement") || s.contains("aluminum")
                || s.contains("chemical") || s.contains("glass") || s.contains("paper")
                || s.contains("manufactur") || s.contains("metal") || s.contains("textile")
                || s.contains("apparel") || s.contains("leather") || s.contains("tobacco")
                || s.contains("beverage") || s.contains("food") || s.contains("lime")
                || s.contains("wood") || s.contains("petrochem") || s.contains("mining")
                || s.contains("quarry")) {
            return "industry";
        }

        // 3. Transportation
        if (s.contains("transport") || s.contains("aviation") || s.contains("shipping")
                || s.contains("railway") || s.contains("road") || s.contains("vessel")) {
            return "transportation";
        }

        // 4. Agriculture & Livestock
        if (s.contains("rice") || s.contains("fertiliz") || s.contains("ferment")
                || s.contains("manure") || s.contains("agricult") || s.contains("crop")
                || s.contains("cattle") || s.contains("soil-emission")) {
            return "agriculture";
        }

        // 5. Fossil Fuel Operations (Extraction, Refining, Transport)
        if (s.contains("oil") || s.contains("gas") || s.contains("coal") || s.contains("fossil")) {
            return "fossil-fuel-operations";
        }

        // 6. Waste Management
        if (s.contains("waste") || s.contains("landfill") || s.contains("incinerat")
                || s.contains("discharge") || s.contains("biogenic")) {
            return "waste";
        }

        // 7. Residential & Commercial Buildings
        if (s.contains("resident") || s.contains("building") || s.contains("onsite-fuel")) {
            return "buildings";
        }

        // 8. Forestry & Land Use
        if (s.contains("forest") || s.contains("land") || s.contains("soil") || s.contains("shrub")
                || s.contains("wetland") || s.contains("removal")) {
            return "forestry-and-land-use";
        }

        return "industry";
    }
}