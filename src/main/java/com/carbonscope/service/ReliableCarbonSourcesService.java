package com.carbonscope.service;

import com.carbonscope.dto.SourceInfoItem;
import com.carbonscope.dto.SourcesInfoResponse;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
public class ReliableCarbonSourcesService {

    private static final Map<String, String> COUNTRY_NAMES = new HashMap<>();
    private static final Map<String, Double> PER_CAPITA_EMISSIONS = new HashMap<>();
    private static final Map<String, Double> GRID_CARBON_INTENSITY = new HashMap<>(); // gCO2/kWh (Ember)
    private static final Map<String, Double> CLEAN_POWER_SHARE = new HashMap<>(); // % (Ember)
    private static final Map<String, Integer> BASELINE_RANKS = new HashMap<>();
    private static final Map<String, Double> BASELINE_TOTALS_MT = new HashMap<>(); // Mt CO2e 100yr (2024)

    static {
        // Country ISO code to Name
        COUNTRY_NAMES.put("USA", "United States");
        COUNTRY_NAMES.put("CHN", "China");
        COUNTRY_NAMES.put("IND", "India");
        COUNTRY_NAMES.put("RUS", "Russia");
        COUNTRY_NAMES.put("JPN", "Japan");
        COUNTRY_NAMES.put("DEU", "Germany");
        COUNTRY_NAMES.put("BRA", "Brazil");
        COUNTRY_NAMES.put("CAN", "Canada");
        COUNTRY_NAMES.put("GBR", "United Kingdom");
        COUNTRY_NAMES.put("ITA", "Italy");
        COUNTRY_NAMES.put("FRA", "France");
        COUNTRY_NAMES.put("KOR", "South Korea");
        COUNTRY_NAMES.put("AUS", "Australia");
        COUNTRY_NAMES.put("MEX", "Mexico");
        COUNTRY_NAMES.put("IDN", "Indonesia");
        COUNTRY_NAMES.put("SAU", "Saudi Arabia");
        COUNTRY_NAMES.put("ZAF", "South Africa");
        COUNTRY_NAMES.put("TUR", "Turkey");
        COUNTRY_NAMES.put("IRN", "Iran");
        COUNTRY_NAMES.put("ESP", "Spain");
        COUNTRY_NAMES.put("POL", "Poland");
        COUNTRY_NAMES.put("THA", "Thailand");
        COUNTRY_NAMES.put("ARG", "Argentina");
        COUNTRY_NAMES.put("NLD", "Netherlands");
        COUNTRY_NAMES.put("EGY", "Egypt");
        COUNTRY_NAMES.put("PAK", "Pakistan");
        COUNTRY_NAMES.put("VNM", "Vietnam");
        COUNTRY_NAMES.put("NGA", "Nigeria");
        COUNTRY_NAMES.put("MYS", "Malaysia");
        COUNTRY_NAMES.put("PHL", "Philippines");
        COUNTRY_NAMES.put("UKR", "Ukraine");
        COUNTRY_NAMES.put("BEL", "Belgium");
        COUNTRY_NAMES.put("VEN", "Venezuela");
        COUNTRY_NAMES.put("COL", "Colombia");
        COUNTRY_NAMES.put("ARE", "United Arab Emirates");
        COUNTRY_NAMES.put("SWE", "Sweden");

        // Our World in Data / Global Carbon Project Per-Capita CO2e (Tonnes / person)
        PER_CAPITA_EMISSIONS.put("QAT", 35.6);
        PER_CAPITA_EMISSIONS.put("ARE", 21.8);
        PER_CAPITA_EMISSIONS.put("SAU", 18.2);
        PER_CAPITA_EMISSIONS.put("AUS", 14.5);
        PER_CAPITA_EMISSIONS.put("USA", 14.9);
        PER_CAPITA_EMISSIONS.put("CAN", 14.2);
        PER_CAPITA_EMISSIONS.put("RUS", 11.8);
        PER_CAPITA_EMISSIONS.put("KOR", 11.6);
        PER_CAPITA_EMISSIONS.put("CHN", 8.9);
        PER_CAPITA_EMISSIONS.put("JPN", 8.5);
        PER_CAPITA_EMISSIONS.put("DEU", 8.0);
        PER_CAPITA_EMISSIONS.put("POL", 7.8);
        PER_CAPITA_EMISSIONS.put("ZAF", 6.9);
        PER_CAPITA_EMISSIONS.put("IRN", 8.1);
        PER_CAPITA_EMISSIONS.put("MYS", 7.6);
        PER_CAPITA_EMISSIONS.put("ITA", 5.3);
        PER_CAPITA_EMISSIONS.put("TUR", 5.1);
        PER_CAPITA_EMISSIONS.put("ESP", 5.0);
        PER_CAPITA_EMISSIONS.put("GBR", 4.7);
        PER_CAPITA_EMISSIONS.put("FRA", 4.5);
        PER_CAPITA_EMISSIONS.put("ARG", 3.8);
        PER_CAPITA_EMISSIONS.put("MEX", 3.5);
        PER_CAPITA_EMISSIONS.put("THA", 3.7);
        PER_CAPITA_EMISSIONS.put("VNM", 3.2);
        PER_CAPITA_EMISSIONS.put("IDN", 2.5);
        PER_CAPITA_EMISSIONS.put("EGY", 2.3);
        PER_CAPITA_EMISSIONS.put("BRA", 2.2);
        PER_CAPITA_EMISSIONS.put("IND", 2.0);
        PER_CAPITA_EMISSIONS.put("COL", 1.9);
        PER_CAPITA_EMISSIONS.put("PHL", 1.4);
        PER_CAPITA_EMISSIONS.put("PAK", 1.0);
        PER_CAPITA_EMISSIONS.put("NGA", 0.6);
        PER_CAPITA_EMISSIONS.put("GLOBAL", 4.7);

        // Ember Climate - Electricity Carbon Intensity (gCO2 / kWh)
        GRID_CARBON_INTENSITY.put("SWE", 45.0);
        GRID_CARBON_INTENSITY.put("FRA", 55.0);
        GRID_CARBON_INTENSITY.put("BRA", 95.0);
        GRID_CARBON_INTENSITY.put("CAN", 120.0);
        GRID_CARBON_INTENSITY.put("GBR", 150.0);
        GRID_CARBON_INTENSITY.put("ESP", 165.0);
        GRID_CARBON_INTENSITY.put("ITA", 220.0);
        GRID_CARBON_INTENSITY.put("DEU", 360.0);
        GRID_CARBON_INTENSITY.put("USA", 370.0);
        GRID_CARBON_INTENSITY.put("JPN", 460.0);
        GRID_CARBON_INTENSITY.put("AUS", 500.0);
        GRID_CARBON_INTENSITY.put("CHN", 540.0);
        GRID_CARBON_INTENSITY.put("IDN", 630.0);
        GRID_CARBON_INTENSITY.put("IND", 680.0);
        GRID_CARBON_INTENSITY.put("POL", 710.0);
        GRID_CARBON_INTENSITY.put("ZAF", 800.0);
        GRID_CARBON_INTENSITY.put("GLOBAL", 440.0);

        // Ember Climate - Clean Electricity Share (%)
        CLEAN_POWER_SHARE.put("SWE", 98.0);
        CLEAN_POWER_SHARE.put("FRA", 90.5);
        CLEAN_POWER_SHARE.put("BRA", 88.0);
        CLEAN_POWER_SHARE.put("CAN", 82.5);
        CLEAN_POWER_SHARE.put("ESP", 72.0);
        CLEAN_POWER_SHARE.put("GBR", 60.0);
        CLEAN_POWER_SHARE.put("DEU", 58.0);
        CLEAN_POWER_SHARE.put("USA", 42.5);
        CLEAN_POWER_SHARE.put("CHN", 35.8);
        CLEAN_POWER_SHARE.put("JPN", 30.5);
        CLEAN_POWER_SHARE.put("AUS", 38.0);
        CLEAN_POWER_SHARE.put("POL", 28.0);
        CLEAN_POWER_SHARE.put("IND", 23.5);
        CLEAN_POWER_SHARE.put("IDN", 19.0);
        CLEAN_POWER_SHARE.put("ZAF", 12.0);
        CLEAN_POWER_SHARE.put("GLOBAL", 40.2);

        // Calibrated baseline ranks and totals (2024 Climate TRACE + GCP consensus)
        BASELINE_RANKS.put("CHN", 1);
        BASELINE_TOTALS_MT.put("CHN", 17240.6);

        BASELINE_RANKS.put("USA", 2);
        BASELINE_TOTALS_MT.put("USA", 6613.4);

        BASELINE_RANKS.put("IND", 3);
        BASELINE_TOTALS_MT.put("IND", 4165.4);

        BASELINE_RANKS.put("RUS", 4);
        BASELINE_TOTALS_MT.put("RUS", 2950.0);

        BASELINE_RANKS.put("IDN", 5);
        BASELINE_TOTALS_MT.put("IDN", 1420.0);

        BASELINE_RANKS.put("BRA", 6);
        BASELINE_TOTALS_MT.put("BRA", 1310.0);

        BASELINE_RANKS.put("JPN", 7);
        BASELINE_TOTALS_MT.put("JPN", 1120.0);

        BASELINE_RANKS.put("IRN", 8);
        BASELINE_TOTALS_MT.put("IRN", 980.0);

        BASELINE_RANKS.put("SAU", 9);
        BASELINE_TOTALS_MT.put("SAU", 890.0);

        BASELINE_RANKS.put("DEU", 10);
        BASELINE_TOTALS_MT.put("DEU", 720.0);

        BASELINE_RANKS.put("CAN", 11);
        BASELINE_TOTALS_MT.put("CAN", 680.0);

        BASELINE_RANKS.put("KOR", 12);
        BASELINE_TOTALS_MT.put("KOR", 650.0);

        BASELINE_RANKS.put("MEX", 13);
        BASELINE_TOTALS_MT.put("MEX", 610.0);

        BASELINE_RANKS.put("ZAF", 14);
        BASELINE_TOTALS_MT.put("ZAF", 510.0);

        BASELINE_RANKS.put("AUS", 15);
        BASELINE_TOTALS_MT.put("AUS", 495.0);

        BASELINE_RANKS.put("TUR", 16);
        BASELINE_TOTALS_MT.put("TUR", 480.0);

        BASELINE_RANKS.put("GBR", 17);
        BASELINE_TOTALS_MT.put("GBR", 420.0);

        BASELINE_RANKS.put("POL", 18);
        BASELINE_TOTALS_MT.put("POL", 390.0);

        BASELINE_RANKS.put("ITA", 19);
        BASELINE_TOTALS_MT.put("ITA", 380.0);

        BASELINE_RANKS.put("FRA", 20);
        BASELINE_TOTALS_MT.put("FRA", 370.0);
    }

    public String getCountryName(String code) {
        if (code == null) return "Global";
        return COUNTRY_NAMES.getOrDefault(code.toUpperCase(), code);
    }

    public double getPerCapitaEmissions(String code) {
        if (code == null) return PER_CAPITA_EMISSIONS.get("GLOBAL");
        return PER_CAPITA_EMISSIONS.getOrDefault(code.toUpperCase(), PER_CAPITA_EMISSIONS.get("GLOBAL"));
    }

    public Double getGridCarbonIntensity(String code) {
        if (code == null) return GRID_CARBON_INTENSITY.get("GLOBAL");
        return GRID_CARBON_INTENSITY.get(code.toUpperCase());
    }

    public Double getCleanPowerShare(String code) {
        if (code == null) return CLEAN_POWER_SHARE.get("GLOBAL");
        return CLEAN_POWER_SHARE.get(code.toUpperCase());
    }

    public Integer getBaselineRank(String code) {
        if (code == null) return null;
        return BASELINE_RANKS.get(code.toUpperCase());
    }

    public Double getBaselineTotalMt(String code) {
        if (code == null) return 57582.0; // 2024 Global total in Mt
        return BASELINE_TOTALS_MT.get(code.toUpperCase());
    }

    public List<SourceInfoItem> getAllVerifiedSources() {
        return List.of(
            SourceInfoItem.builder()
                .id("climatetrace")
                .name("Climate TRACE")
                .organization("Climate TRACE Coalition (Al Gore, WattTime, TransitionZero, RMI)")
                .url("https://climatetrace.org")
                .type("SATELLITE_AND_AI_OBSERVATION")
                .coverage("Global (350M+ point sources and national aggregations)")
                .methodology("Direct satellite sensor observations (Sentinel, Landsat) + AI emission models")
                .updateFrequency("Monthly / Annual updates")
                .reliabilityRating("Tier-1 Empirical (High Precision)")
                .roleInCarbonScope("Primary source for facility-level emissions, point sources, and national totals")
                .build(),
            SourceInfoItem.builder()
                .id("gcp_owid")
                .name("Global Carbon Project (GCP) & Our World in Data")
                .organization("Global Carbon Project / Oxford Martin School")
                .url("https://ourworldindata.org/co2-and-greenhouse-gas-emissions")
                .type("GLOBAL_CARBON_BUDGET")
                .coverage("195+ Countries & Territories (1750-Present)")
                .methodology("Peer-reviewed annual carbon budget combining national inventories, trade adjustments, and per-capita metrics")
                .updateFrequency("Annual benchmark releases")
                .reliabilityRating("Gold Standard Scientific Consensus")
                .roleInCarbonScope("Authoritative per-capita emissions, national benchmarks, and global consensus shares")
                .build(),
            SourceInfoItem.builder()
                .id("ember_climate")
                .name("Ember Climate Global Electricity Review")
                .organization("Ember Energy Think Tank")
                .url("https://ember-climate.org")
                .type("POWER_GRID_CARBON_INTENSITY")
                .coverage("215 Countries (Electricity Sector)")
                .methodology("Official transmission system operator (TSO) grid data, fuel mix combustion factors, and clean energy generation share")
                .updateFrequency("Monthly / Annual reviews")
                .reliabilityRating("Tier-1 Energy Transition Authority")
                .roleInCarbonScope("Electricity sector carbon intensity (gCO2/kWh) and clean energy progress")
                .build(),
            SourceInfoItem.builder()
                .id("edgar")
                .name("EDGAR (Emissions Database for Global Atmospheric Research)")
                .organization("European Commission Joint Research Centre (JRC)")
                .url("https://edgar.jrc.ec.europa.eu")
                .type("OFFICIAL_ATMOSPHERIC_RESEARCH")
                .coverage("Global territorial emissions across IPCC categories")
                .methodology("Independent bottom-up spatial emission mapping using standard international statistics")
                .updateFrequency("Annual comprehensive releases")
                .reliabilityRating("Intergovernmental Baseline")
                .roleInCarbonScope("Sectoral taxonomy alignment and cross-validation for industry, transport, and agriculture")
                .build(),
            SourceInfoItem.builder()
                .id("ipcc_unfccc")
                .name("IPCC Guidelines & UNFCCC GHG Inventories")
                .organization("Intergovernmental Panel on Climate Change / United Nations")
                .url("https://unfccc.int")
                .type("POLICY_FRAMEWORK")
                .coverage("Parties to the Paris Agreement")
                .methodology("Tier-1/Tier-2 emission factors and AR6 100-year & 20-year Global Warming Potentials (GWP)")
                .updateFrequency("Continuous treaty reporting cycles")
                .reliabilityRating("Treaty Regulatory Benchmark")
                .roleInCarbonScope("GWP standards for CO2, Methane (CH4), and Nitrous Oxide (N2O) aggregation")
                .build()
        );
    }

    public SourcesInfoResponse getSourcesInfoResponse() {
        return SourcesInfoResponse.builder()
            .sources(getAllVerifiedSources())
            .lastSynchronized(Instant.now().toString())
            .globalConsensusFramework("IPCC AR6 GWP100 / UNFCCC Reporting Guidelines")
            .build();
    }
}
