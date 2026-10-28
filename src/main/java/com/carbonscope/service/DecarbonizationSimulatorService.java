package com.carbonscope.service;

import com.carbonscope.dto.ProvenanceDto;
import com.carbonscope.dto.simulator.*;
import com.carbonscope.exception.SimulatorValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class DecarbonizationSimulatorService {

    private final ReliableCarbonSourcesService reliableSourcesService;

    // -----------------------------------------------------------------------------------------
    // 1. Direct Operational Combustion Emission Factors
    //    Derived from IPCC 2006 Guidelines for National Greenhouse Gas Inventories
    //    (Vol 2 Energy, Ch 2 Stationary Combustion, Table 2.2, Net Calorific Value basis in kg CO2/TJ)
    //    Boundary: Direct chimney/stack fuel combustion (Scope 1).
    //    Formula: EF_electric (g/kWh) = [Fuel EF (kg/TJ) * 0.0036 TJ/MWh] / Plant Thermal Efficiency
    //    Equivalently: EF_electric (g/kWh) = EF_fuel (kg/TJ) * 0.0036 / efficiency
    //    - Coal: 95,000 kg CO2/TJ * 0.0036 / 0.360 = 950.0 g/kWh
    //    - Gas CCGT: 56,100 kg CO2/TJ * 0.0036 / 0.4925 = 410.07 g/kWh (rounded to 410.0 g/kWh)
    //    - Oil Peaker: 77,400 kg CO2/TJ * 0.0036 / 0.3870 = 720.0 g/kWh
    // -----------------------------------------------------------------------------------------
    public static final double EF_COAL_DIRECT = 950.0;       // Derived: 95,000 kg CO2/TJ fuel factor @ 36.0% fleet thermal efficiency
    public static final double EF_FOSSIL_GAS_DIRECT = 410.0;  // Derived: 56,100 kg CO2/TJ natural gas factor @ 49.25% CCGT thermal efficiency
    public static final double EF_OIL_DIRECT = 720.0;         // Derived: 77,400 kg CO2/TJ residual fuel oil @ 38.7% peaker thermal efficiency
    public static final double EF_CLEAN_DIRECT = 0.0;         // Zero direct chimney/stack combustion emissions (Scope 1)

    // Direct Operational combustion sensitivity range (IPCC 2006 Stationary Combustion Table 2.2 efficiency range)
    public static final double EF_COAL_DIRECT_MIN = 855.0;      // Supercritical fleet @ 40.0% efficiency
    public static final double EF_COAL_DIRECT_MEDIAN = 950.0;   // Median fleet @ 36.0% efficiency
    public static final double EF_COAL_DIRECT_MAX = 1036.36;    // Aging subcritical fleet @ 33.0% efficiency

    public static final double EF_FOSSIL_GAS_DIRECT_MIN = 367.20; // Advanced CCGT @ 55.0% efficiency
    public static final double EF_FOSSIL_GAS_DIRECT_MEDIAN = 410.0; // Fleet median @ 49.25% efficiency
    public static final double EF_FOSSIL_GAS_DIRECT_MAX = 448.80; // Older CCGT @ 45.0% efficiency

    public static final double EF_OIL_DIRECT_MIN = 663.43;      // Peaker @ 42.0% efficiency
    public static final double EF_OIL_DIRECT_MEDIAN = 720.0;    // Peaker median @ 38.7% efficiency
    public static final double EF_OIL_DIRECT_MAX = 796.11;      // Peaker @ 35.0% efficiency

    public static final String SOURCE_DIRECT_IPCC = "Derived from IPCC 2006 Guidelines (Vol 2 Energy, Ch 2 Table 2.2 NCV fuel combustion factors in kg/TJ): EF_electric (g/kWh) = EF_fuel (kg/TJ) * 0.0036 / efficiency. Coal: 95,000 kg/TJ @ η=36.0%, Gas CCGT: 56,100 kg/TJ @ η=49.25%, Oil: 77,400 kg/TJ @ η=38.7%";
    public static final int YEAR_DIRECT_IPCC = 2006;

    // -----------------------------------------------------------------------------------------
    // 2. Full Life-Cycle Emission Factors (IPCC AR5 WGIII Annex III, Table A.III.2)
    //    Source: Schlömer et al., 2014: Technology-specific cost and performance parameters.
    //    Boundary: Cradle-to-grave (Scope 1+2+3). Global 50th percentile medians, and literature Min/Max in gCO2eq / kWh.
    // -----------------------------------------------------------------------------------------
    public static final double EF_COAL_LIFECYCLE = 820.0;         // Table A.III.2 Coal - PC median: 820 gCO2eq/kWh (Min: 740, Max: 910)
    public static final double EF_COAL_LCA_MIN = 740.0;
    public static final double EF_COAL_LCA_MEDIAN = 820.0;
    public static final double EF_COAL_LCA_MAX = 910.0;

    public static final double EF_FOSSIL_GAS_LIFECYCLE = 490.0;    // Table A.III.2 Natural Gas - Combined Cycle median: 490 gCO2eq/kWh (Min: 410, Max: 650)
    public static final double EF_FOSSIL_GAS_LCA_MIN = 410.0;
    public static final double EF_FOSSIL_GAS_LCA_MEDIAN = 490.0;
    public static final double EF_FOSSIL_GAS_LCA_MAX = 650.0;

    public static final double EF_OIL_LIFECYCLE = 750.0;           // PROVISIONAL / UNVERIFIED in Table A.III.2 (No dedicated entry; provisional peaker estimate)
    public static final double EF_OIL_LCA_MIN = 500.0;
    public static final double EF_OIL_LCA_MEDIAN = 750.0;
    public static final double EF_OIL_LCA_MAX = 800.0;

    public static final double EF_SOLAR_PV_LIFECYCLE = 48.0;       // Table A.III.2 Solar PV - utility median: 48 gCO2eq/kWh (Min: 18, Max: 180)
    public static final double EF_SOLAR_PV_LCA_MIN = 18.0;
    public static final double EF_SOLAR_PV_LCA_MEDIAN = 48.0;
    public static final double EF_SOLAR_PV_LCA_MAX = 180.0;

    public static final double EF_WIND_ONSHORE_LIFECYCLE = 11.0;   // Table A.III.2 Wind - onshore median: 11 gCO2eq/kWh (Min: 7.0, Max: 56)
    public static final double EF_WIND_ONSHORE_LCA_MIN = 7.0;
    public static final double EF_WIND_ONSHORE_LCA_MEDIAN = 11.0;
    public static final double EF_WIND_ONSHORE_LCA_MAX = 56.0;

    public static final double EF_WIND_OFFSHORE_LIFECYCLE = 12.0;  // Table A.III.2 Wind - offshore median: 12 gCO2eq/kWh (Min: 8.0, Max: 35)
    public static final double EF_WIND_OFFSHORE_LCA_MIN = 8.0;
    public static final double EF_WIND_OFFSHORE_LCA_MEDIAN = 12.0;
    public static final double EF_WIND_OFFSHORE_LCA_MAX = 35.0;

    public static final double EF_NUCLEAR_LIFECYCLE = 12.0;        // Table A.III.2 Nuclear median: 12 gCO2eq/kWh (Min: 3.7, Max: 110)
    public static final double EF_NUCLEAR_LCA_MIN = 3.7;
    public static final double EF_NUCLEAR_LCA_MEDIAN = 12.0;
    public static final double EF_NUCLEAR_LCA_MAX = 110.0;

    public static final double EF_HYDRO_LIFECYCLE = 24.0;          // Table A.III.2 Hydropower median: 24 gCO2eq/kWh (Min: 1.0, Max: 2200)
    public static final double EF_HYDRO_LCA_MIN = 1.0;
    public static final double EF_HYDRO_LCA_MEDIAN = 24.0;
    public static final double EF_HYDRO_LCA_MAX = 2200.0;

    public static final String SOURCE_LIFECYCLE_IPCC = "IPCC AR5 WGIII Annex III Table A.III.2 (Schlömer et al., 2014, Technology-specific cost and performance parameters, 50th percentile medians; Min/Max literature ranges)";
    public static final int YEAR_LIFECYCLE_IPCC = 2014;

    public static final String EMBER_DATASET_NAME = "Ember Climate - Yearly Electricity Data (GER 2024 / 2023 Consolidated Annual Baseline, Snapshot 2024-05)";
    public static final String EMBER_URL = "https://files.ember-energy.org/public-downloads/yearly_full_release_long_format.csv";
    public static final String EMBER_METHODOLOGY_URL = "https://ember-climate.org/creative-commons/data-methodology/";
    public static final String EMBER_SOURCE_FILE = "yearly_full_release_long_format.csv";
    public static final String EMBER_SNAPSHOT_DATE = "2024-05-15";
    public static final String EMBER_SOURCE_COLUMNS = "Category: 'Electricity generation' & 'Power sector emissions' (Value, Unit: TWh, mtCO2, gCO2/kWh)";
    public static final String EMBER_SOURCE_PUBLISHED_METRICS = "Total Generation (TWh), Coal Generation (TWh), Gas Generation (TWh), Oil Generation (TWh), Clean Generation (TWh), Power Sector Emissions (mtCO2)";
    public static final String EMBER_DERIVED_METRICS = "Total Fossil Generation (sum: Coal+Gas+Oil), Clean Power Share % (ratio: Clean/Total*100), Grid Carbon Intensity gCO2e/kWh (ratio: Emissions*1000/Total Generation)";
    public static final String EMBER_VERIFICATION_STATUS = "VERIFIED_SOURCE_OBSERVATION";
    public static final String EMBER_PUBLISHER = "Ember (ember-climate.org)";
    public static final int EMBER_BASELINE_YEAR = 2023; // Consolidated peer-reviewed reporting year in GER 2024

    private static final Map<String, CountryElectricityProfileDto> COUNTRY_PROFILES = new LinkedHashMap<>();

    static {
        // Ember Climate Yearly Electricity Data (Consolidated 2023 observations from GER 2024 release)
        // Clean generation includes: Hydro + Nuclear + Solar + Wind + Bioenergy + Other Renewables
        // Internal Category Balance: Coal + Gas + Oil/Other + Clean == Total Generation
        // Ember Emissions Metric: Power sector lifecycle GHG emissions (MtCO2e, IPCC AR5 Annex 3 methodology)
        // Ember Intensity: Lifecycle GHG intensity gCO2e/kWh = (Power Sector Emissions Mt * 1000) / Total Generation TWh
        registerProfile("IND", "India", 1918.26, 1445.44, 50.80, 3.85, 1500.09, 418.17, 21.80, 1369.09, 713.72,
                "Hydro: 149.43 TWh, Solar: 115.42 TWh, Wind: 82.11 TWh, Nuclear: 48.20 TWh, Bioenergy: 23.01 TWh");
        registerProfile("CHN", "China", 9456.45, 5752.74, 297.80, 76.97, 6127.51, 3328.94, 35.21, 5515.45, 583.25,
                "Hydro: 1226.00 TWh, Solar: 584.15 TWh, Wind: 885.87 TWh, Nuclear: 434.72 TWh, Bioenergy: 198.20 TWh");
        registerProfile("USA", "United States", 4253.91, 675.11, 1806.06, 33.13, 2514.30, 1739.61, 40.90, 1671.37, 392.90,
                "Hydro: 239.01 TWh, Solar: 238.94 TWh, Wind: 421.14 TWh, Nuclear: 774.87 TWh, Bioenergy: 47.20 TWh");
        registerProfile("DEU", "Germany", 506.72, 124.78, 76.66, 20.96, 222.40, 284.32, 56.11, 184.24, 363.59,
                "Hydro: 21.24 TWh, Solar: 63.87 TWh, Wind: 140.54 TWh, Nuclear: 7.22 TWh, Bioenergy: 51.26 TWh");
        registerProfile("GBR", "United Kingdom", 292.53, 3.78, 101.66, 10.82, 116.26, 176.27, 60.25, 68.92, 235.60,
                "Hydro: 5.54 TWh, Solar: 13.88 TWh, Wind: 82.15 TWh, Nuclear: 40.60 TWh, Bioenergy: 34.10 TWh");
        registerProfile("JPN", "Japan", 1006.73, 326.93, 346.98, 32.50, 706.41, 300.32, 29.82, 495.91, 492.60,
                "Hydro: 74.53 TWh, Solar: 96.36 TWh, Wind: 9.96 TWh, Nuclear: 77.46 TWh, Bioenergy: 42.01 TWh");
        registerProfile("AUS", "Australia", 273.11, 125.61, 46.67, 5.76, 178.04, 95.07, 34.81, 152.08, 556.85,
                "Hydro: 15.08 TWh, Solar: 44.99 TWh, Wind: 31.87 TWh, Nuclear: 0.00 TWh, Bioenergy: 3.13 TWh");
        registerProfile("BRA", "Brazil", 708.12, 13.31, 38.59, 11.53, 63.43, 644.69, 91.05, 68.16, 96.26,
                "Hydro: 426.00 TWh, Solar: 50.63 TWh, Wind: 95.80 TWh, Nuclear: 14.50 TWh, Bioenergy: 57.76 TWh");
        registerProfile("ZAF", "South Africa", 232.95, 192.29, 0.00, 3.68, 195.97, 36.98, 15.88, 166.42, 714.40,
                "Hydro: 2.00 TWh, Solar: 14.81 TWh, Wind: 11.60 TWh, Nuclear: 8.10 TWh, Bioenergy: 0.47 TWh");
        registerProfile("IDN", "Indonesia", 350.60, 216.78, 62.04, 6.58, 285.40, 65.20, 18.59, 238.77, 681.03,
                "Hydro: 24.59 TWh, Solar: 0.71 TWh, Wind: 0.48 TWh, Nuclear: 0.00 TWh, Bioenergy: 22.48 TWh");
        registerProfile("POL", "Poland", 165.52, 98.85, 16.49, 4.32, 119.66, 45.86, 27.71, 107.73, 650.86,
                "Hydro: 2.41 TWh, Solar: 11.11 TWh, Wind: 24.18 TWh, Nuclear: 0.00 TWh, Bioenergy: 8.16 TWh");
        registerProfile("VNM", "Vietnam", 276.59, 129.76, 26.36, 1.70, 157.82, 118.77, 42.94, 131.30, 474.71,
                "Hydro: 80.62 TWh, Solar: 25.69 TWh, Wind: 11.59 TWh, Nuclear: 0.00 TWh, Bioenergy: 0.87 TWh");
        registerProfile("CAN", "Canada", 641.39, 23.74, 102.88, 6.89, 133.51, 507.88, 79.19, 111.88, 174.43,
                "Hydro: 360.74 TWh, Solar: 8.40 TWh, Wind: 40.51 TWh, Nuclear: 88.43 TWh, Bioenergy: 9.80 TWh");
        registerProfile("FRA", "France", 518.75, 1.61, 30.02, 9.43, 41.06, 477.69, 92.09, 27.63, 53.26,
                "Hydro: 55.90 TWh, Solar: 22.59 TWh, Wind: 50.48 TWh, Nuclear: 338.20 TWh, Bioenergy: 9.94 TWh");
        registerProfile("ITA", "Italy", 261.55, 13.22, 119.02, 12.79, 145.03, 116.52, 44.55, 84.56, 323.30,
                "Hydro: 40.52 TWh, Solar: 30.71 TWh, Wind: 23.59 TWh, Nuclear: 0.00 TWh, Bioenergy: 16.01 TWh");
        registerProfile("ESP", "Spain", 279.11, 3.62, 64.36, 10.48, 78.46, 200.65, 71.89, 47.36, 169.68,
                "Hydro: 25.00 TWh, Solar: 48.59 TWh, Wind: 64.28 TWh, Nuclear: 56.87 TWh, Bioenergy: 5.90 TWh");
    }

    private static void registerProfile(String code, String name, double total, double coal, double gas,
                                        double oil, double fossil, double clean, double cleanShare,
                                        double emissionsMt, double intensity, String cleanBreakdown) {
        COUNTRY_PROFILES.put(code, CountryElectricityProfileDto.builder()
                .countryCode(code)
                .countryName(name)
                .totalGenerationTwh(total)
                .coalGenerationTwh(coal)
                .gasGenerationTwh(gas)
                .oilGenerationTwh(oil)
                .totalFossilGenerationTwh(fossil)
                .cleanGenerationTwh(clean)
                .cleanPowerSharePct(cleanShare)
                .powerSectorEmissionsMt(emissionsMt)
                .gridCarbonIntensityGco2PerKwh(intensity)
                .dataSource(EMBER_DATASET_NAME)
                .datasetUrl(EMBER_URL)
                .sourceFile(EMBER_SOURCE_FILE)
                .sourceColumns(EMBER_SOURCE_COLUMNS)
                .verificationStatus(EMBER_VERIFICATION_STATUS)
                .publisher(EMBER_PUBLISHER)
                .baselineYear(EMBER_BASELINE_YEAR)
                .accountingBoundary("LIFECYCLE_GHG_CO2E")
                .metricDefinition("Power sector lifecycle GHG emissions (CO2, CH4, N2O) in MtCO2e (100-year GWP, IPCC AR5 Annex 3 methodology) and reported generation under mixed national gross/net reporting conventions.")
                .reconciliationStatus("INTERNAL_CATEGORY_RECONCILED (Category balance invariant verified against Ember yearly release snapshot 2024-05)")
                .generationAccountingConvention("MIXED_GROSS_NET_BY_COUNTRY (Eurostat/EU countries report gross generation; non-EU countries follow national agency conventions; auxiliary station use not universally deducted)")
                .cleanTechnologiesBreakdown(cleanBreakdown)
                .datasetSnapshotDate(EMBER_SNAPSHOT_DATE)
                .methodologyUrl(EMBER_METHODOLOGY_URL)
                .sourcePublishedMetrics(EMBER_SOURCE_PUBLISHED_METRICS)
                .derivedMetrics(EMBER_DERIVED_METRICS)
                .build());
    }

    public List<CountryElectricityProfileDto> getSupportedCountries() {
        return new ArrayList<>(COUNTRY_PROFILES.values());
    }

    public List<String> getSupportedCountryCodes() {
        return new ArrayList<>(COUNTRY_PROFILES.keySet());
    }

    public CountryElectricityProfileDto getCountryProfile(String countryCode) {
        if (countryCode == null || countryCode.isBlank()) {
            throw new SimulatorValidationException("Country code is required.");
        }
        CountryElectricityProfileDto profile = COUNTRY_PROFILES.get(countryCode.trim().toUpperCase());
        if (profile == null) {
            throw new SimulatorValidationException(String.format(
                    "Country '%s' is not supported. Supported countries with verified Ember 2024 generation accounts: %s",
                    countryCode, getSupportedCountryCodes()
            ));
        }
        return profile;
    }

    public DecarbonizationScenarioResponse calculateScenario(DecarbonizationScenarioRequest request) {
        String countryCode = request != null && request.getCountryCode() != null
                ? request.getCountryCode().trim().toUpperCase()
                : "";
        CountryElectricityProfileDto profile = getCountryProfile(countryCode);

        // Accounting boundary normalization
        String requestedBoundary = request.getAccountingBoundary() != null && !request.getAccountingBoundary().isBlank()
                ? request.getAccountingBoundary().trim().toUpperCase()
                : "OPERATIONAL_SCOPE_1";

        if (!"OPERATIONAL_SCOPE_1".equals(requestedBoundary) && !"LIFECYCLE_SCOPE_1_2_3".equals(requestedBoundary)) {
            throw new SimulatorValidationException(
                    "Unsupported accounting boundary '" + request.getAccountingBoundary() +
                    "'. Supported boundaries: ['OPERATIONAL_SCOPE_1', 'LIFECYCLE_SCOPE_1_2_3']."
            );
        }

        validateRequest(request, profile);

        boolean isLcaMode = "LIFECYCLE_SCOPE_1_2_3".equals(requestedBoundary);
        boolean isSingleLegacy = (request.getInterventions() == null || request.getInterventions().isEmpty());
        List<ScenarioInterventionDto> interventions;

        if (isSingleLegacy) {
            String unit = request.getGenerationUnit().trim().toUpperCase();
            double amount = request.getGenerationAmount();
            double genTwh = "GWH".equals(unit) ? (amount / 1000.0) : amount;
            String displacedFuel = request.getDisplacedFuel().trim().toUpperCase();
            double maxAvailableTwh = getAvailableFossilGeneration(profile, displacedFuel);
            String fuelDisplayName = getFuelDisplayName(displacedFuel);

            // Legacy backward compatibility: unconstrained single intervention check
            if (request.getConstraints() == null && genTwh > maxAvailableTwh + 1e-6) {
                throw new SimulatorValidationException(String.format(
                        "Physically impossible intervention: requested displacement of %.2f TWh exceeds maximum available %s generation for %s (%.2f TWh). Maximum allowed displacement is %.2f TWh.",
                        genTwh, fuelDisplayName, profile.getCountryName(), maxAvailableTwh, maxAvailableTwh
                ));
            }

            ScenarioInterventionDto single = ScenarioInterventionDto.builder()
                    .id("intervention-1")
                    .cleanSource(request.getReplacementCleanSource())
                    .generationAmount(request.getGenerationAmount())
                    .generationUnit(request.getGenerationUnit())
                    .targetDisplacedFuel(request.getDisplacedFuel())
                    .customDisplacedEmissionFactor(request.getCustomDisplacedEmissionFactor())
                    .customDisplacedFactorSource(request.getCustomDisplacedFactorSource())
                    .customReplacementEmissionFactor(request.getCustomReplacementEmissionFactor())
                    .customReplacementFactorSource(request.getCustomReplacementFactorSource())
                    .build();
            interventions = new ArrayList<>();
            interventions.add(single);
        } else {
            interventions = request.getInterventions();
        }

        // -----------------------------------------------------------------------------------------
        // Dispatch & Physical Constraints Setup
        // -----------------------------------------------------------------------------------------
        ScenarioConstraintsDto constraints = request.getConstraints();
        double minCoal = (constraints != null && constraints.getMinResidualCoalTwh() != null) ? constraints.getMinResidualCoalTwh() : 0.0;
        double minGas = (constraints != null && constraints.getMinResidualGasTwh() != null) ? constraints.getMinResidualGasTwh() : 0.0;
        double minOil = (constraints != null && constraints.getMinResidualOilTwh() != null) ? constraints.getMinResidualOilTwh() : 0.0;
        double minFossil = (constraints != null && constraints.getMinResidualFossilTwh() != null) ? constraints.getMinResidualFossilTwh() : 0.0;
        double maxCleanCap = (constraints != null && constraints.getMaxCleanAdditionLimitTwh() != null) ? constraints.getMaxCleanAdditionLimitTwh() : Double.MAX_VALUE;

        double coalAvailable = profile.getCoalGenerationTwh();
        double gasAvailable = profile.getGasGenerationTwh();
        double oilAvailable = profile.getOilGenerationTwh();
        double totalFossilAvailable = profile.getTotalFossilGenerationTwh();

        double reducibleCoal = Math.max(0.0, coalAvailable - minCoal);
        double reducibleGas = Math.max(0.0, gasAvailable - minGas);
        double reducibleOil = Math.max(0.0, oilAvailable - minOil);
        double reducibleTotalFossil = Math.max(0.0, totalFossilAvailable - Math.max(minFossil, (minCoal + minGas + minOil)));

        double remainingCleanCap = maxCleanCap;
        double coalDisplacedAcc = 0.0;
        double gasDisplacedAcc = 0.0;
        double oilDisplacedAcc = 0.0;

        List<InterventionResultDto> itemizedResults = new ArrayList<>();
        double totalRequestedCleanTwh = 0.0;
        double totalDisplacedFossilTwh = 0.0;
        double totalUnusedCleanTwh = 0.0;

        double totalAvoidedDirectMt = 0.0;
        double totalGrossDisplacedLcaMt = 0.0;
        double totalCleanEmbodiedLcaMt = 0.0;
        double totalNetAvoidedLcaMt = 0.0;

        double compositeMinRaw = 0.0;
        double compositeMedRaw = 0.0;
        double compositeMaxRaw = 0.0;

        boolean hasOilLca = false;
        boolean hasNegativeAbatement = false;
        StringBuilder constraintNotesSummary = new StringBuilder();

        // -----------------------------------------------------------------------------------------
        // Sequential Dispatch Simulation
        // -----------------------------------------------------------------------------------------
        for (int i = 0; i < interventions.size(); i++) {
            ScenarioInterventionDto item = interventions.get(i);
            String cleanSource = item.getCleanSource().trim().toUpperCase();
            String targetFuel = item.getTargetDisplacedFuel().trim().toUpperCase();
            String unit = item.getGenerationUnit() != null ? item.getGenerationUnit().trim().toUpperCase() : "TWH";

            double requestedTwh = "GWH".equals(unit) ? (item.getGenerationAmount() / 1000.0) : item.getGenerationAmount();
            double requestedGwh = "GWH".equals(unit) ? item.getGenerationAmount() : (item.getGenerationAmount() * 1000.0);
            totalRequestedCleanTwh += requestedTwh;

            if ("OIL".equals(targetFuel) && isLcaMode) {
                hasOilLca = true;
            }

            // A. Clean capacity cap constraint
            double cleanAllowedTwh = Math.min(requestedTwh, remainingCleanCap);
            remainingCleanCap = Math.max(0.0, remainingCleanCap - cleanAllowedTwh);

            // B. Fossil fuel displacement pool
            double displacedTwh;
            switch (targetFuel) {
                case "COAL" -> {
                    displacedTwh = Math.min(cleanAllowedTwh, Math.min(reducibleCoal, reducibleTotalFossil));
                    reducibleCoal -= displacedTwh;
                    coalDisplacedAcc += displacedTwh;
                }
                case "FOSSIL_GAS", "GAS" -> {
                    displacedTwh = Math.min(cleanAllowedTwh, Math.min(reducibleGas, reducibleTotalFossil));
                    reducibleGas -= displacedTwh;
                    gasDisplacedAcc += displacedTwh;
                }
                case "OIL" -> {
                    displacedTwh = Math.min(cleanAllowedTwh, Math.min(reducibleOil, reducibleTotalFossil));
                    reducibleOil -= displacedTwh;
                    oilDisplacedAcc += displacedTwh;
                }
                case "GRID_FOSSIL_MIX" -> {
                    displacedTwh = Math.min(cleanAllowedTwh, reducibleTotalFossil);
                    double sumRemaining = reducibleCoal + reducibleGas + reducibleOil;
                    if (sumRemaining > 0) {
                        double dCoal = displacedTwh * (reducibleCoal / sumRemaining);
                        double dGas = displacedTwh * (reducibleGas / sumRemaining);
                        double dOil = displacedTwh * (reducibleOil / sumRemaining);
                        reducibleCoal -= dCoal; coalDisplacedAcc += dCoal;
                        reducibleGas -= dGas; gasDisplacedAcc += dGas;
                        reducibleOil -= dOil; oilDisplacedAcc += dOil;
                    }
                }
                default -> throw new SimulatorValidationException("Unsupported displaced fuel: " + targetFuel);
            }
            reducibleTotalFossil -= displacedTwh;
            totalDisplacedFossilTwh += displacedTwh;

            double unusedCleanTwh = requestedTwh - displacedTwh;
            totalUnusedCleanTwh += unusedCleanTwh;
            double displacedGwh = displacedTwh * 1000.0;

            boolean isCapped = unusedCleanTwh > 1e-6;
            String constraintNote;
            if (!isCapped) {
                constraintNote = "Fully dispatched (100% substitution efficiency).";
            } else if (cleanAllowedTwh < requestedTwh - 1e-6) {
                constraintNote = String.format("Clean addition capped at %.2f TWh (%.2f TWh surplus) due to maximum clean addition limit (%.2f TWh).",
                        cleanAllowedTwh, unusedCleanTwh, maxCleanCap);
                constraintNotesSummary.append(String.format("Intervention %d (%s): Clean addition capped by max clean limit. ", i + 1, getCleanDisplayName(cleanSource)));
            } else if (minCoal > 0 || minGas > 0 || minOil > 0 || minFossil > 0) {
                constraintNote = String.format("Displacement capped at %.2f TWh (%.2f TWh surplus clean generation) to maintain minimum residual fossil generation constraints.",
                        displacedTwh, unusedCleanTwh);
                constraintNotesSummary.append(String.format("Intervention %d (%s -> %s): Displacement constrained by minimum residual fossil baseload. ",
                        i + 1, getCleanDisplayName(cleanSource), getFuelDisplayName(targetFuel)));
            } else {
                constraintNote = String.format("Displacement capped at %.2f TWh (%.2f TWh surplus clean generation) because available %s generation is exhausted.",
                        displacedTwh, unusedCleanTwh, getFuelDisplayName(targetFuel));
                constraintNotesSummary.append(String.format("Intervention %d (%s -> %s): Available %s generation exhausted. ",
                        i + 1, getCleanDisplayName(cleanSource), getFuelDisplayName(targetFuel), getFuelDisplayName(targetFuel)));
            }

            // C. Avoided Emissions Accounting
            double dispFactorDirect = getDisplacedFactorDirect(profile, targetFuel, item.getCustomDisplacedEmissionFactor());
            double dispFactorLca = getDisplacedFactorLifecycle(profile, targetFuel, item.getCustomDisplacedEmissionFactor());
            double repFactorLca = getReplacementFactorLifecycle(cleanSource, item.getCustomReplacementEmissionFactor());

            double avoidedDirectMt = (displacedGwh * dispFactorDirect) / 1_000_000.0;
            double grossDisplacedLcaMt = (displacedGwh * dispFactorLca) / 1_000_000.0;
            double cleanEmbodiedLcaMt = (displacedGwh * repFactorLca) / 1_000_000.0;
            double netAvoidedLcaMt = grossDisplacedLcaMt - cleanEmbodiedLcaMt;

            if (netAvoidedLcaMt < 0) {
                hasNegativeAbatement = true;
            }

            totalAvoidedDirectMt += avoidedDirectMt;
            totalGrossDisplacedLcaMt += grossDisplacedLcaMt;
            totalCleanEmbodiedLcaMt += cleanEmbodiedLcaMt;
            totalNetAvoidedLcaMt += netAvoidedLcaMt;

            // D. Sensitivity Bounds for this intervention
            double[] dispBounds = getDisplacedBounds(profile, targetFuel, item.getCustomDisplacedEmissionFactor(), isLcaMode);
            double[] repBounds = getReplacementBounds(cleanSource, item.getCustomReplacementEmissionFactor(), isLcaMode);

            double itemLower;
            double itemMed;
            double itemUpper;

            if (isLcaMode) {
                itemLower = (displacedGwh * (dispBounds[0] - repBounds[2])) / 1_000_000.0;
                itemMed = (displacedGwh * (dispBounds[1] - repBounds[1])) / 1_000_000.0;
                itemUpper = (displacedGwh * (dispBounds[2] - repBounds[0])) / 1_000_000.0;
            } else {
                itemLower = (displacedGwh * dispBounds[0]) / 1_000_000.0;
                itemMed = (displacedGwh * dispBounds[1]) / 1_000_000.0;
                itemUpper = (displacedGwh * dispBounds[2]) / 1_000_000.0;
            }

            compositeMinRaw += itemLower;
            compositeMedRaw += itemMed;
            compositeMaxRaw += itemUpper;

            double itemMinOrdered = itemLower;
            double itemMaxOrdered = itemUpper;

            InterventionResultDto resultItem = InterventionResultDto.builder()
                    .interventionIndex(i + 1)
                    .interventionId(item.getId() != null ? item.getId() : ("intervention-" + (i + 1)))
                    .cleanSource(cleanSource)
                    .cleanSourceDisplayName(getCleanDisplayName(cleanSource))
                    .targetDisplacedFuel(targetFuel)
                    .targetFuelDisplayName(getFuelDisplayName(targetFuel))
                    .requestedCleanGenerationTwh(requestedTwh)
                    .displacedFossilGenerationTwh(displacedTwh)
                    .unusedCleanGenerationTwh(unusedCleanTwh)
                    .grossAvoidedMt(isLcaMode ? grossDisplacedLcaMt : avoidedDirectMt)
                    .cleanReplacementLifecycleMt(isLcaMode ? cleanEmbodiedLcaMt : 0.0)
                    .netAvoidedMt(isLcaMode ? netAvoidedLcaMt : avoidedDirectMt)
                    .sensitivityMinMt(itemMinOrdered)
                    .sensitivityMedianMt(itemMed)
                    .sensitivityMaxMt(itemMaxOrdered)
                    .isCapped(isCapped)
                    .constraintNote(constraintNote)
                    .build();

            itemizedResults.add(resultItem);
        }

        // -----------------------------------------------------------------------------------------
        // Composite Totals & Ordering
        // -----------------------------------------------------------------------------------------
        double primaryGrossAvoidedMt = isLcaMode ? totalGrossDisplacedLcaMt : totalAvoidedDirectMt;
        double primaryCleanReplacementEmissionsMt = isLcaMode ? totalCleanEmbodiedLcaMt : 0.0;
        double primaryNetAvoidedMt = isLcaMode ? totalNetAvoidedLcaMt : totalAvoidedDirectMt;
        double primaryNetAvoidedTonnes = primaryNetAvoidedMt * 1_000_000.0;

        double compMinOrdered = compositeMinRaw;
        double compMaxOrdered = compositeMaxRaw;
        double compMedOrdered = compositeMedRaw;

        String abatementWarning = hasNegativeAbatement
                ? "Warning: One or more replacement clean technologies have lifecycle factors exceeding displaced fossil fuel factors; intervention leads to a net atmospheric emissions increase."
                : null;

        boolean constraintsApplied = (constraints != null && (
                constraints.getMinResidualCoalTwh() != null ||
                constraints.getMinResidualGasTwh() != null ||
                constraints.getMinResidualOilTwh() != null ||
                constraints.getMinResidualFossilTwh() != null ||
                constraints.getMaxCleanAdditionLimitTwh() != null
        )) || totalUnusedCleanTwh > 1e-6;

        String constraintSummary = constraintsApplied
                ? (constraintNotesSummary.length() > 0 ? constraintNotesSummary.toString().trim() : "Dispatch constraints active; fossil generation floors respected.")
                : "No binding dispatch constraints. 100% substitution efficiency achieved across all interventions.";

        // Invariant: Generation balance under static 1:1 displacement
        double totalGenerationTwh = profile.getTotalGenerationTwh();
        double simulatedCoalTwh = Math.max(0.0, coalAvailable - coalDisplacedAcc);
        double simulatedGasTwh = Math.max(0.0, gasAvailable - gasDisplacedAcc);
        double simulatedOilTwh = Math.max(0.0, oilAvailable - oilDisplacedAcc);
        double simulatedFossilGenerationTwh = Math.max(0.0, totalFossilAvailable - totalDisplacedFossilTwh);
        double simulatedCleanGenerationTwh = profile.getCleanGenerationTwh() + totalDisplacedFossilTwh;
        double simulatedCleanPowerSharePct = (simulatedCleanGenerationTwh / totalGenerationTwh) * 100.0;
        double cleanShareDelta = simulatedCleanPowerSharePct - profile.getCleanPowerSharePct();

        String unavailableReason;
        String emissionsBoundaryNote;
        if (isLcaMode) {
            unavailableReason = "Derived post-scenario national inventory metrics (simulated power sector emissions, national total emissions, and simulated grid intensity) are DISABLED and UNAVAILABLE. Net lifecycle abatement (" + String.format("%.2f", totalNetAvoidedLcaMt) + " MtCO₂e) models global cradle-to-grave atmospheric mitigation across transnational supply chains, which cannot be subtracted from Ember's national territorial power sector inventory without violating geographic and sectoral accounting boundaries.";
            emissionsBoundaryNote = "Post-intervention national inventory metrics are disabled (UNAVAILABLE) to prevent subtracting transnational lifecycle supply-chain abatement from a national territorial electricity sector baseline.";
        } else {
            unavailableReason = "Direct operational post-intervention inventory (simulated power sector emissions and simulated grid intensity) is UNAVAILABLE because the baseline dataset (Ember) reports full-lifecycle GHG emissions (MtCO₂e, IPCC AR5) rather than a direct operational Scope 1 fuel-combustion-only inventory. Avoided direct stack emissions (" + String.format("%.2f", totalAvoidedDirectMt) + " MtCO₂) are reported strictly as an independent conditional estimate using generic IPCC 2006 factors.";
            emissionsBoundaryNote = "Simulated national emissions inventory and simulated grid intensity are disabled (UNAVAILABLE) to prevent subtracting direct operational Scope 1 emissions from Ember's lifecycle GHG (MtCO₂e) baseline.";
        }

        // Assemble DTOs
        Double nationalTotalMt = reliableSourcesService.getBaselineTotalMt(countryCode);
        if (nationalTotalMt == null) nationalTotalMt = profile.getPowerSectorEmissionsMt();

        double baselineTargetFuel;
        double simulatedTargetFuel;
        Set<String> targetFuels = new HashSet<>();
        for (ScenarioInterventionDto item : interventions) {
            targetFuels.add(item.getTargetDisplacedFuel().trim().toUpperCase());
        }
        if (targetFuels.size() == 1) {
            String fuel = targetFuels.iterator().next();
            if ("FOSSIL_GAS".equals(fuel) || "GAS".equals(fuel)) {
                baselineTargetFuel = profile.getGasGenerationTwh();
                simulatedTargetFuel = simulatedGasTwh;
            } else if ("OIL".equals(fuel)) {
                baselineTargetFuel = profile.getOilGenerationTwh();
                simulatedTargetFuel = simulatedOilTwh;
            } else if ("COAL".equals(fuel)) {
                baselineTargetFuel = profile.getCoalGenerationTwh();
                simulatedTargetFuel = simulatedCoalTwh;
            } else {
                baselineTargetFuel = profile.getTotalFossilGenerationTwh();
                simulatedTargetFuel = simulatedFossilGenerationTwh;
            }
        } else {
            baselineTargetFuel = profile.getTotalFossilGenerationTwh();
            simulatedTargetFuel = simulatedFossilGenerationTwh;
        }

        DecarbonizationBaselineDto baselineDto = DecarbonizationBaselineDto.builder()
                .totalGenerationTwh(profile.getTotalGenerationTwh())
                .fossilGenerationTwh(profile.getTotalFossilGenerationTwh())
                .targetFuelGenerationTwh(baselineTargetFuel)
                .cleanGenerationTwh(profile.getCleanGenerationTwh())
                .cleanPowerSharePct(profile.getCleanPowerSharePct())
                .powerSectorEmissionsMt(profile.getPowerSectorEmissionsMt())
                .gridCarbonIntensityGco2PerKwh(profile.getGridCarbonIntensityGco2PerKwh())
                .nationalTotalEmissionsMt(nationalTotalMt)
                .sourceName(profile.getDataSource())
                .datasetUrl(profile.getDatasetUrl())
                .sourceFile(profile.getSourceFile())
                .sourceColumns(profile.getSourceColumns())
                .verificationStatus(profile.getVerificationStatus())
                .reportingYear(profile.getBaselineYear())
                .accountingBoundary("LIFECYCLE_GHG_CO2E")
                .metricDefinition("Power sector lifecycle GHG emissions (CO2, CH4, N2O) in MtCO2e (100-year GWP, IPCC AR5 Annex 3 methodology).")
                .generationAccountingConvention(profile.getGenerationAccountingConvention())
                .reconciliationStatus(profile.getReconciliationStatus())
                .cleanTechnologiesIncluded("Hydro, Nuclear, Solar PV, Wind (Onshore & Offshore), Bioenergy, Geothermal")
                .cleanTechnologiesBreakdown(profile.getCleanTechnologiesBreakdown())
                .datasetSnapshotDate(profile.getDatasetSnapshotDate())
                .methodologyUrl(profile.getMethodologyUrl())
                .sourcePublishedMetrics(profile.getSourcePublishedMetrics())
                .derivedMetrics(profile.getDerivedMetrics())
                .build();

        DecarbonizationResultsDto scenarioDto = DecarbonizationResultsDto.builder()
                .totalGenerationTwh(totalGenerationTwh)
                .simulatedFossilGenerationTwh(simulatedFossilGenerationTwh)
                .simulatedTargetFuelGenerationTwh(simulatedTargetFuel)
                .simulatedCleanGenerationTwh(simulatedCleanGenerationTwh)
                .simulatedCleanPowerSharePct(simulatedCleanPowerSharePct)
                .simulatedPowerSectorEmissionsMt(null)
                .simulatedGridCarbonIntensityGco2PerKwh(null)
                .simulatedNationalTotalEmissionsMt(null)
                .emissionsBoundaryNote(emissionsBoundaryNote)
                .totalRequestedCleanGenerationTwh(totalRequestedCleanTwh)
                .totalDisplacedFossilGenerationTwh(totalDisplacedFossilTwh)
                .totalUnusedCleanGenerationTwh(totalUnusedCleanTwh)
                .simulatedCoalGenerationTwh(simulatedCoalTwh)
                .simulatedGasGenerationTwh(simulatedGasTwh)
                .simulatedOilGenerationTwh(simulatedOilTwh)
                .constraintsApplied(constraintsApplied)
                .constraintEnforcementSummary(constraintSummary)
                .build();

        ScenarioInterventionDto first = interventions.get(0);
        double[] firstDispBounds = getDisplacedBounds(profile, first.getTargetDisplacedFuel(), first.getCustomDisplacedEmissionFactor(), isLcaMode);
        double[] firstRepBounds = getReplacementBounds(first.getCleanSource(), first.getCustomReplacementEmissionFactor(), isLcaMode);

        String sensitivityDesc = isLcaMode
                ? (hasOilLca
                    ? (interventions.size() > 1
                        ? "Composite uncertainty propagated across " + interventions.size() + " interventions. [PROVISIONAL / UNVERIFIED IN TABLE A.III.2] Dedicated oil-fired power lifecycle factor is not published in IPCC AR5 Table A.III.2; provisional peaker estimate (500–800 gCO2e/kWh) applied. Verified technologies (Coal, Gas, Solar, Wind, Nuclear, Hydro) reflect IPCC AR5 WGIII Table A.III.2 literature ranges."
                        : "[PROVISIONAL / UNVERIFIED IN TABLE A.III.2] Dedicated oil-fired power lifecycle factor is not published in IPCC AR5 Table A.III.2; provisional peaker estimate (500–800 gCO2e/kWh) applied. Verified technologies (Coal, Gas, Solar, Wind, Nuclear, Hydro) reflect IPCC AR5 WGIII Table A.III.2 literature ranges.")
                    : (interventions.size() == 1
                        ? "IPCC AR5 WGIII Annex III (Table A.III.2) literature harmonized lifecycle emission factor distribution (Schlömer et al., 2014). Represents global variation across published LCA studies in solar insolation, wind capacity factors, regional manufacturing grid carbon intensity, operational lifetimes, and upstream fuel methane fugitives."
                        : "Composite uncertainty propagated from IPCC AR5 WGIII Annex III Table A.III.2 literature harmonized factor distributions (Schlömer et al., 2014) across " + interventions.size() + " interventions."))
                : (interventions.size() == 1
                    ? "IPCC 2006 Guidelines for National Greenhouse Gas Inventories (Vol 2 Energy, Ch 2 Stationary Combustion Table 2.2). Represents power plant thermal conversion efficiency range: high efficiency (supercritical/advanced CCGT) to lower efficiency (aging subcritical/older CCGT)."
                    : "Composite uncertainty propagated from IPCC 2006 stationary combustion thermal efficiency variations across " + interventions.size() + " interventions.");

        String sensitivityCitation = isLcaMode
                ? (hasOilLca
                    ? "Provisional Peaker Estimate & IPCC AR5 WGIII Annex III Table A.III.2 (Schlömer et al., 2014)"
                    : (interventions.size() == 1
                        ? "IPCC AR5 WGIII Annex III Table A.III.2 (Lifecycle metrics in gCO2eq/kWh: literature min, 50th percentile median, literature max)"
                        : SOURCE_LIFECYCLE_IPCC))
                : (interventions.size() == 1
                    ? "Derived from IPCC 2006 Vol 2 Energy Table 2.2 stationary fuel combustion defaults divided by fleet thermal efficiency range."
                    : SOURCE_DIRECT_IPCC);

        SensitivityIntervalDto sensitivityInterval = SensitivityIntervalDto.builder()
                .minAvoidedEmissionsMt(compMinOrdered)
                .medianAvoidedEmissionsMt(compMedOrdered)
                .maxAvoidedEmissionsMt(compMaxOrdered)
                .displacedFactorMinGco2PerKwh(firstDispBounds[0])
                .displacedFactorMedianGco2PerKwh(firstDispBounds[1])
                .displacedFactorMaxGco2PerKwh(firstDispBounds[2])
                .replacementFactorMinGco2PerKwh(firstRepBounds[0])
                .replacementFactorMedianGco2PerKwh(firstRepBounds[1])
                .replacementFactorMaxGco2PerKwh(firstRepBounds[2])
                .emissionFactorUnit(isLcaMode ? "gCO2e/kWh" : "gCO2/kWh")
                .emissionsUnit(isLcaMode ? "Mt CO2e/yr" : "Mt CO2/yr")
                .uncertaintyDescription(sensitivityDesc)
                .sourceCitation(sensitivityCitation)
                .publicationYear(isLcaMode ? YEAR_LIFECYCLE_IPCC : YEAR_DIRECT_IPCC)
                .accountingBoundary(requestedBoundary)
                .build();

        DecarbonizationImpactDto impactDto = DecarbonizationImpactDto.builder()
                .grossAvoidedEmissionsMt(primaryGrossAvoidedMt)
                .cleanReplacementLifecycleEmissionsMt(primaryCleanReplacementEmissionsMt)
                .netAvoidedEmissionsMt(primaryNetAvoidedMt)
                .netAvoidedEmissionsTonnes(primaryNetAvoidedTonnes)
                .powerSectorEmissionsReductionPct(null)
                .nationalEmissionsReductionPct(null)
                .gridIntensityDeltaGco2PerKwh(null)
                .cleanShareDeltaPct(cleanShareDelta)
                .avoidedDirectEmissionsMt(totalAvoidedDirectMt)
                .simulatedDirectPowerEmissionsMt(null)
                .simulatedDirectGridIntensityGco2PerKwh(null)
                .grossDisplacedLifecycleMt(totalGrossDisplacedLcaMt)
                .cleanEmbodiedLifecycleMt(totalCleanEmbodiedLcaMt)
                .netAvoidedLifecycleMt(totalNetAvoidedLcaMt)
                .netNegativeAbatement(hasNegativeAbatement)
                .netAbatementWarning(abatementWarning)
                .simulatedMetricsUnavailableReason(unavailableReason)
                .sensitivityInterval(sensitivityInterval)
                .itemizedInterventions(itemizedResults)
                .totalDisplacedFossilGenerationTwh(totalDisplacedFossilTwh)
                .totalUnusedCleanGenerationTwh(totalUnusedCleanTwh)
                .constraintsApplied(constraintsApplied)
                .dispatchConstraintSummary(constraintSummary)
                .build();

        ScenarioAssumptionsDto assumptionsDto = isSingleLegacy
                ? buildSingleAssumptionsDto(
                        first.getTargetDisplacedFuel(), totalDisplacedFossilTwh, totalDisplacedFossilTwh * 1000.0,
                        first.getCleanSource(), request, isLcaMode,
                        getDisplacedFactorDirect(profile, first.getTargetDisplacedFuel(), first.getCustomDisplacedEmissionFactor()),
                        getDisplacedFactorLifecycle(profile, first.getTargetDisplacedFuel(), first.getCustomDisplacedEmissionFactor()),
                        getReplacementFactorLifecycle(first.getCleanSource(), first.getCustomReplacementEmissionFactor()),
                        profile, interventions, constraints
                )
                : buildCompositeAssumptionsDto(
                        interventions, constraints, request, isLcaMode, profile, totalDisplacedFossilTwh
                );

        List<String> limitations = List.of(
                "Emissions Boundary Incompatibility & Inventory Metric Disablement: Ember power sector emissions and intensity baselines represent full-lifecycle GHG emissions (MtCO₂e, IPCC AR5 Annex 3 including upstream methane leaks and infrastructure supply chain), NOT direct operational chimney combustion (Scope 1 CO₂). Neither direct operational reductions (" + String.format("%.2f", totalAvoidedDirectMt) + " MtCO₂) nor net lifecycle abatement (" + String.format("%.2f", totalNetAvoidedLcaMt) + " MtCO₂e) can be subtracted from Ember's baseline without violating emissions accounting boundaries. Derived post-scenario national emissions and grid intensity are disabled as UNAVAILABLE.",
                "Direct 1:1 Energy Displacement is an Analytical Scenario Assumption: 1:1 displacement is a hypothetical policy benchmark, not an observed or dynamic market dispatch outcome. Real-world dispatch depends on merit-order dynamics, ramping constraints, capacity factors, transmission congestion, and grid stability.",
                "Basic Dispatch Constraints Implemented: The model enforces physical non-negativity and user-specified minimum residual fossil generation constraints. It does NOT perform dynamic unit commitment, alternating current power flow (AC-OPF), or chronological hourly dispatch.",
                "Energy Storage Excluded: Zero battery energy storage systems (BESS) or pumped hydro storage (PHS) are modeled; round-trip storage efficiency losses (typically 15-25%) are outside the scenario boundary.",
                "Diurnal Timing & Baseload Ramping Excluded: Solar generation occurs exclusively during daylight hours and cannot displace 24/7 nighttime baseload coal without storage and flexible dispatch. Thermal minimum generation constraints are user-configurable but not dynamically optimized.",
                "Transmission Losses & Curtailment Excluded: Grid transmission losses (typically 5-10%) and commercial losses are excluded.",
                "Demand Load Growth Excluded: Scenario models clean energy as replacing existing fossil output; in rapidly expanding power grids, new clean power predominantly serves incremental demand load growth rather than decommissioning running thermal plants.",
                "Generic Scientific Defaults: Operational emission factors (950 g/kWh Coal, 410 g/kWh Gas, 720 g/kWh Oil) are engineering derivations from IPCC 2006 stationary fuel combustion defaults (NCV basis in kg/TJ) divided by fleet thermal efficiency: EF_electric = EF_fuel * 0.0036 / efficiency.",
                "Mixed Gross/Net Generation Accounting: Ember's reported generation data reflects mixed national reporting conventions."
        );

        ProvenanceDto provenance = ProvenanceDto.builder()
                .status("DETERMINISTIC_MODEL_RESULT")
                .sourceName(profile.getDataSource() + " & IPCC AR5 WGIII Table A.III.2 / 2006 Guidelines")
                .methodology(isLcaMode
                        ? "IPCC AR5 WGIII Annex III Life-Cycle Carbon Accounting (Scope 1+2+3, Table A.III.2, multi-intervention displacement)"
                        : "IPCC 2006 Stationary Combustion Direct Fuel Factor Accounting (Scope 1 conditional avoided emissions; national baseline inventory simulation blocked/unavailable due to Ember lifecycle boundary)")
                .reportingYear(profile.getBaselineYear())
                .empirical(false)
                .temporalCompleteness("CONSOLIDATED_ANNUAL")
                .isPartialOrProvisional(hasOilLca)
                .qualityNotes("Baseline data sourced from Ember Climate (reporting year 2023, snapshot 2024-05). Direct operational Scope 1 post-intervention inventory is disabled because baseline emissions represent lifecycle CO2e (IPCC AR5). Direct avoided emissions are reported strictly as an independent scenario result.")
                .unverifiedFactorWarning(hasOilLca ? "[PROVISIONAL / UNVERIFIED IN TABLE A.III.2] Dedicated oil-fired power lifecycle factor is provisional (500–800 gCO2e/kWh)." : null)
                .build();

        String interventionName;
        if (interventions.size() == 1) {
            ScenarioInterventionDto single = interventions.get(0);
            interventionName = String.format("Displace %.1f TWh %s with %s in %s (%s)",
                    itemizedResults.get(0).getDisplacedFossilGenerationTwh(),
                    getFuelDisplayName(single.getTargetDisplacedFuel()),
                    getCleanDisplayName(single.getCleanSource()),
                    profile.getCountryName(),
                    isLcaMode ? "Life-Cycle Boundary" : "Operational Stack Boundary");
        } else {
            interventionName = String.format("Composite Scenario: %d Interventions in %s (%.1f TWh Clean Added, %.1f TWh Displaced, %s)",
                    interventions.size(), profile.getCountryName(), totalRequestedCleanTwh, totalDisplacedFossilTwh,
                    isLcaMode ? "Life-Cycle Boundary" : "Operational Stack Boundary");
        }

        String formula = isLcaMode
                ? "Net Life-Cycle Avoided (Mt CO₂e) = ∑ [Displaced Generation_i (GWh) × EF_disp_lca,i - Clean Generation_i (GWh) × EF_clean_lca,i] / 1,000,000"
                : "Avoided Direct Stack Emissions (Mt CO₂) = ∑ [Displaced Generation_i (GWh) × EF_disp_direct,i] / 1,000,000";

        String boundaryDesc = isLcaMode
                ? "Life-Cycle Accounting (Scope 1+2+3 / Cradle-to-Grave): Evaluates global atmospheric GHG abatement including upstream fuel extraction, refining, transport, and clean technology supply chains. Modeled under composite multi-intervention displacement."
                : "Operational Stack Combustion Accounting (Scope 1): Evaluates direct chimney emissions avoided from power plant fuel combustion. Note: Simulated post-intervention grid inventory is disabled as UNAVAILABLE because Ember baselines are lifecycle GHG (MtCO₂e).";

        return DecarbonizationScenarioResponse.builder()
                .countryCode(countryCode)
                .countryName(profile.getCountryName())
                .interventionName(interventionName)
                .accountingBoundary(requestedBoundary)
                .boundaryDescription(boundaryDesc)
                .baseline(baselineDto)
                .scenario(scenarioDto)
                .impact(impactDto)
                .assumptions(assumptionsDto)
                .calculationMethod(isLcaMode ? "IPCC AR5 WGIII Life-Cycle Assessment (Table A.III.2)" : "Direct Fuel Combustion Stack Accounting")
                .formula(formula)
                .accountingStandard(isLcaMode ? "IPCC AR5 WGIII Annex III Table A.III.2 & ISO 14040/44" : "IPCC 2006 Guidelines & GHG Protocol Scope 2")
                .limitations(limitations)
                .provenance(provenance)
                .timestamp(Instant.now().toString())
                .build();
    }

    public double getDisplacedFactorDirect(CountryElectricityProfileDto profile, String displacedFuel, Double customFactor) {
        if (customFactor != null) {
            return customFactor;
        }
        return switch (displacedFuel) {
            case "COAL" -> EF_COAL_DIRECT;
            case "FOSSIL_GAS", "GAS" -> EF_FOSSIL_GAS_DIRECT;
            case "OIL" -> EF_OIL_DIRECT;
            case "GRID_FOSSIL_MIX" -> {
                if (profile.getTotalFossilGenerationTwh() > 0) {
                    yield (profile.getPowerSectorEmissionsMt() * 1000.0) / profile.getTotalFossilGenerationTwh();
                }
                yield EF_FOSSIL_GAS_DIRECT;
            }
            default -> throw new SimulatorValidationException("Unsupported displaced fuel: " + displacedFuel);
        };
    }

    public double getDisplacedFactorLifecycle(CountryElectricityProfileDto profile, String displacedFuel, Double customFactor) {
        if (customFactor != null) {
            return customFactor;
        }
        return switch (displacedFuel) {
            case "COAL" -> EF_COAL_LIFECYCLE;
            case "FOSSIL_GAS", "GAS" -> EF_FOSSIL_GAS_LIFECYCLE;
            case "OIL" -> EF_OIL_LIFECYCLE;
            case "GRID_FOSSIL_MIX" -> {
                if (profile.getTotalFossilGenerationTwh() > 0) {
                    yield ((profile.getPowerSectorEmissionsMt() * 1000.0) / profile.getTotalFossilGenerationTwh()) + 70.0;
                }
                yield EF_FOSSIL_GAS_LIFECYCLE;
            }
            default -> throw new SimulatorValidationException("Unsupported displaced fuel: " + displacedFuel);
        };
    }

    public double getReplacementFactorLifecycle(String cleanSource, Double customFactor) {
        if (customFactor != null) {
            return customFactor;
        }
        return switch (cleanSource) {
            case "SOLAR_PV", "SOLAR" -> EF_SOLAR_PV_LIFECYCLE;
            case "WIND_ONSHORE" -> EF_WIND_ONSHORE_LIFECYCLE;
            case "WIND_OFFSHORE" -> EF_WIND_OFFSHORE_LIFECYCLE;
            case "NUCLEAR" -> EF_NUCLEAR_LIFECYCLE;
            case "HYDRO", "HYDROELECTRIC" -> EF_HYDRO_LIFECYCLE;
            default -> throw new SimulatorValidationException("Unsupported clean technology: " + cleanSource);
        };
    }

    private double[] getDisplacedBounds(CountryElectricityProfileDto profile, String displacedFuel, Double customFactor, boolean isLcaMode) {
        if (customFactor != null) {
            return new double[]{customFactor, customFactor, customFactor};
        }
        if (isLcaMode) {
            return switch (displacedFuel) {
                case "COAL" -> new double[]{EF_COAL_LCA_MIN, EF_COAL_LCA_MEDIAN, EF_COAL_LCA_MAX};
                case "FOSSIL_GAS", "GAS" -> new double[]{EF_FOSSIL_GAS_LCA_MIN, EF_FOSSIL_GAS_LCA_MEDIAN, EF_FOSSIL_GAS_LCA_MAX};
                case "OIL" -> new double[]{EF_OIL_LCA_MIN, EF_OIL_LCA_MEDIAN, EF_OIL_LCA_MAX};
                case "GRID_FOSSIL_MIX" -> {
                    double totalFossil = profile.getTotalFossilGenerationTwh();
                    if (totalFossil > 0) {
                        double wc = profile.getCoalGenerationTwh() / totalFossil;
                        double wg = profile.getGasGenerationTwh() / totalFossil;
                        double wo = profile.getOilGenerationTwh() / totalFossil;
                        yield new double[]{
                                (wc * EF_COAL_LCA_MIN) + (wg * EF_FOSSIL_GAS_LCA_MIN) + (wo * EF_OIL_LCA_MIN),
                                (wc * EF_COAL_LCA_MEDIAN) + (wg * EF_FOSSIL_GAS_LCA_MEDIAN) + (wo * EF_OIL_LCA_MEDIAN),
                                (wc * EF_COAL_LCA_MAX) + (wg * EF_FOSSIL_GAS_LCA_MAX) + (wo * EF_OIL_LCA_MAX)
                        };
                    }
                    yield new double[]{EF_FOSSIL_GAS_LCA_MIN, EF_FOSSIL_GAS_LCA_MEDIAN, EF_FOSSIL_GAS_LCA_MAX};
                }
                default -> throw new SimulatorValidationException("Unsupported displaced fuel: " + displacedFuel);
            };
        } else {
            return switch (displacedFuel) {
                case "COAL" -> new double[]{EF_COAL_DIRECT_MIN, EF_COAL_DIRECT_MEDIAN, EF_COAL_DIRECT_MAX};
                case "FOSSIL_GAS", "GAS" -> new double[]{EF_FOSSIL_GAS_DIRECT_MIN, EF_FOSSIL_GAS_DIRECT_MEDIAN, EF_FOSSIL_GAS_DIRECT_MAX};
                case "OIL" -> new double[]{EF_OIL_DIRECT_MIN, EF_OIL_DIRECT_MEDIAN, EF_OIL_DIRECT_MAX};
                case "GRID_FOSSIL_MIX" -> {
                    double totalFossil = profile.getTotalFossilGenerationTwh();
                    if (totalFossil > 0) {
                        double wc = profile.getCoalGenerationTwh() / totalFossil;
                        double wg = profile.getGasGenerationTwh() / totalFossil;
                        double wo = profile.getOilGenerationTwh() / totalFossil;
                        yield new double[]{
                                (wc * EF_COAL_DIRECT_MIN) + (wg * EF_FOSSIL_GAS_DIRECT_MIN) + (wo * EF_OIL_DIRECT_MIN),
                                (wc * EF_COAL_DIRECT_MEDIAN) + (wg * EF_FOSSIL_GAS_DIRECT_MEDIAN) + (wo * EF_OIL_DIRECT_MEDIAN),
                                (wc * EF_COAL_DIRECT_MAX) + (wg * EF_FOSSIL_GAS_DIRECT_MAX) + (wo * EF_OIL_DIRECT_MAX)
                        };
                    }
                    yield new double[]{EF_FOSSIL_GAS_DIRECT_MIN, EF_FOSSIL_GAS_DIRECT_MEDIAN, EF_FOSSIL_GAS_DIRECT_MAX};
                }
                default -> throw new SimulatorValidationException("Unsupported displaced fuel: " + displacedFuel);
            };
        }
    }

    private double[] getReplacementBounds(String cleanSource, Double customFactor, boolean isLcaMode) {
        if (customFactor != null) {
            return new double[]{customFactor, customFactor, customFactor};
        }
        if (isLcaMode) {
            return switch (cleanSource) {
                case "SOLAR_PV", "SOLAR" -> new double[]{EF_SOLAR_PV_LCA_MIN, EF_SOLAR_PV_LCA_MEDIAN, EF_SOLAR_PV_LCA_MAX};
                case "WIND_ONSHORE" -> new double[]{EF_WIND_ONSHORE_LCA_MIN, EF_WIND_ONSHORE_LCA_MEDIAN, EF_WIND_ONSHORE_LCA_MAX};
                case "WIND_OFFSHORE" -> new double[]{EF_WIND_OFFSHORE_LCA_MIN, EF_WIND_OFFSHORE_LCA_MEDIAN, EF_WIND_OFFSHORE_LCA_MAX};
                case "NUCLEAR" -> new double[]{EF_NUCLEAR_LCA_MIN, EF_NUCLEAR_LCA_MEDIAN, EF_NUCLEAR_LCA_MAX};
                case "HYDRO", "HYDROELECTRIC" -> new double[]{EF_HYDRO_LCA_MIN, EF_HYDRO_LCA_MEDIAN, EF_HYDRO_LCA_MAX};
                default -> throw new SimulatorValidationException("Unsupported clean technology: " + cleanSource);
            };
        } else {
            return new double[]{0.0, 0.0, 0.0};
        }
    }

    private ScenarioAssumptionsDto buildSingleAssumptionsDto(
            String displacedFuel,
            double genTwh,
            double genGwh,
            String replacementCleanSource,
            DecarbonizationScenarioRequest req,
            boolean isLcaMode,
            double displacedFactorDirect,
            double displacedFactorLca,
            double replacementFactorLca,
            CountryElectricityProfileDto profile,
            List<ScenarioInterventionDto> interventions,
            ScenarioConstraintsDto constraints
    ) {
        double activeDisplacedFactor = isLcaMode ? displacedFactorLca : displacedFactorDirect;
        double activeReplacementFactor = isLcaMode ? replacementFactorLca : 0.0;

        List<String> exclusions = List.of(
                "Energy Storage Excluded: Zero battery energy storage systems (BESS) or pumped hydro storage (PHS) modeled; no round-trip efficiency losses accounted for.",
                "Diurnal & Seasonal Timing Excluded: Solar generation occurs exclusively during daylight hours; does not displace nighttime baseload coal without storage.",
                "Thermal Ramping & Minimum Plant Loading Excluded: Thermal power plants have minimum technical generation limits (typically 40-55% for coal) and ramping rate limits; static 1:1 displacement does not model boiler thermal cycling or dynamic unit commitment.",
                "Grid Transmission Losses & Bottlenecks Excluded: Interstate transmission losses (3-5%) and AT&C losses (15-20%) outside model boundary.",
                "Renewable Curtailment Excluded: Curtailment due to transmission congestion or localized overgeneration is excluded.",
                "Demand Load Growth Excluded: Scenario models clean additions as replacing existing fossil output; in growing grids, clean power predominantly meets incremental demand load growth."
        );

        String derivationMethod = isLcaMode
                ? "IPCC AR5 WGIII Annex III (Table A.III.2) literature harmonized lifecycle emission factors."
                : "Generic engineering conversion derived from IPCC 2006 stationary fuel combustion defaults (NCV basis in kg/TJ) divided by fleet thermal efficiency: EF_electric (g/kWh) = EF_fuel (kg/TJ) * 0.0036 / efficiency (formula verified against unit conversion: kg/TJ * 1000 g/kg * 3.6e-6 TJ/kWh = 0.0036 g/kWh per kg/TJ).";

        String heatingValueBasis = isLcaMode
                ? "LCA deliverable fuel basis (IPCC AR5 Annex 3)"
                : "Net Calorific Value (NCV) / Lower Heating Value (LHV)";

        return ScenarioAssumptionsDto.builder()
                .displacedFuel(displacedFuel)
                .displacedGenerationTwh(genTwh)
                .displacedGenerationGwh(genGwh)
                .displacedEmissionFactorGco2PerKwh(activeDisplacedFactor)
                .displacedFactorSource(isLcaMode ? SOURCE_LIFECYCLE_IPCC : SOURCE_DIRECT_IPCC)
                .displacedFactorPublicationYear(isLcaMode ? YEAR_LIFECYCLE_IPCC : YEAR_DIRECT_IPCC)
                .displacedFactorBoundary(isLcaMode ? "LIFECYCLE_SCOPE_1_2_3" : "DIRECT_COMBUSTION_SCOPE_1")
                .displacedFactorRange(isLcaMode
                        ? "IPCC AR5 WGIII Table A.III.2 literature harmonized 50th percentile (median)"
                        : "IPCC 2006 stationary combustion defaults with fleet thermal efficiency variation")
                .displacedFactorDerivationMethod(derivationMethod)
                .displacedFactorHeatingValueBasis(heatingValueBasis)
                .replacementSource(replacementCleanSource)
                .replacementGenerationTwh(genTwh)
                .replacementGenerationGwh(genGwh)
                .replacementEmissionFactorGco2PerKwh(activeReplacementFactor)
                .replacementFactorSource(isLcaMode ? SOURCE_LIFECYCLE_IPCC : "Zero Stack Emissions")
                .replacementFactorPublicationYear(isLcaMode ? YEAR_LIFECYCLE_IPCC : 2006)
                .replacementFactorBoundary(isLcaMode ? "LIFECYCLE_SCOPE_1_2_3" : "DIRECT_COMBUSTION_SCOPE_1")
                .displacementModelingType("Analytical 1:1 Static Energy Scenario (Assumption). Does not represent dynamic dispatch, merit-order market equilibrium, curtailment, or grid transmission/storage losses.")
                .gwpConvention(isLcaMode ? "IPCC AR6 100-year GWP (CO2=1, CH4=27.9, N2O=273)" : "IPCC 2006 Guidelines direct CO2-only (Scope 1 stack combustion)")
                .geographicAccountingScope(isLcaMode
                        ? "Transnational cradle-to-grave lifecycle boundary (upstream fuel extraction, refining, transport, infrastructure manufacture, and power generation)"
                        : "National territorial boundary (Scope 1 direct stack emissions within the reporting country)")
                .dispatchEquivalenceCaveat("Hypothetical marginal displacement assumption: every 1 MWh of clean electricity replaces exactly 1 MWh of targeted fossil generation. In real-world grids, marginal generators vary hourly based on fuel prices, heat rates, ramping constraints, and grid stability.")
                .documentedExclusions(exclusions)
                .customDisplacedFactor(req.getCustomDisplacedEmissionFactor() != null)
                .customDisplacedFactorSource(req.getCustomDisplacedFactorSource())
                .customReplacementFactor(req.getCustomReplacementEmissionFactor() != null)
                .customReplacementFactorSource(req.getCustomReplacementFactorSource())
                .configuredInterventions(interventions)
                .configuredConstraints(constraints)
                .dispatchConstraintDescription("Standard dispatch under physical non-negative fossil generation bounds (no additional residual baseload constraints specified).")
                .build();
    }

    private ScenarioAssumptionsDto buildCompositeAssumptionsDto(
            List<ScenarioInterventionDto> interventions,
            ScenarioConstraintsDto constraints,
            DecarbonizationScenarioRequest req,
            boolean isLcaMode,
            CountryElectricityProfileDto profile,
            double totalDisplacedFossilTwh
    ) {
        ScenarioInterventionDto first = interventions.get(0);
        String displacedFuel = interventions.size() == 1 ? first.getTargetDisplacedFuel() : "MULTI_FUEL_DISPLACEMENT";
        String replacementSource = interventions.size() == 1 ? first.getCleanSource() : "MULTI_CLEAN_TECHNOLOGY";

        double activeDisplacedFactor = isLcaMode
                ? getDisplacedFactorLifecycle(profile, first.getTargetDisplacedFuel(), first.getCustomDisplacedEmissionFactor())
                : getDisplacedFactorDirect(profile, first.getTargetDisplacedFuel(), first.getCustomDisplacedEmissionFactor());
        double activeReplacementFactor = isLcaMode
                ? getReplacementFactorLifecycle(first.getCleanSource(), first.getCustomReplacementEmissionFactor())
                : 0.0;

        List<String> exclusions = List.of(
                "Energy Storage Excluded: Zero battery energy storage systems (BESS) or pumped hydro storage (PHS) modeled; no round-trip efficiency losses accounted for.",
                "Diurnal & Seasonal Timing Excluded: Solar generation occurs exclusively during daylight hours; does not displace nighttime baseload coal without storage.",
                "Thermal Ramping Excluded: Minimum technical generation constraints are enforced as static user-configurable floors, not dynamic hourly unit commitment.",
                "Grid Transmission Losses & Bottlenecks Excluded: Interstate transmission losses (3-5%) and AT&C losses (15-20%) outside model boundary.",
                "Renewable Curtailment Excluded: Curtailment is reported only when clean generation exceeds reducible fossil capacity under user constraints.",
                "Demand Load Growth Excluded: Scenario models clean additions as replacing existing fossil output; in growing grids, clean power predominantly meets incremental demand load growth."
        );

        String dispatchConstraintDesc = constraints != null
                ? String.format("Dispatch constraints applied: minResidualCoal=%.1f TWh, minResidualGas=%.1f TWh, minResidualFossil=%.1f TWh, maxCleanLimit=%.1f TWh.",
                constraints.getMinResidualCoalTwh() != null ? constraints.getMinResidualCoalTwh() : 0.0,
                constraints.getMinResidualGasTwh() != null ? constraints.getMinResidualGasTwh() : 0.0,
                constraints.getMinResidualFossilTwh() != null ? constraints.getMinResidualFossilTwh() : 0.0,
                constraints.getMaxCleanAdditionLimitTwh() != null ? constraints.getMaxCleanAdditionLimitTwh() : -1.0)
                : "Standard dispatch under physical non-negative fossil generation bounds (no additional residual baseload constraints specified).";

        return ScenarioAssumptionsDto.builder()
                .displacedFuel(displacedFuel)
                .displacedGenerationTwh(totalDisplacedFossilTwh)
                .displacedGenerationGwh(totalDisplacedFossilTwh * 1000.0)
                .displacedEmissionFactorGco2PerKwh(activeDisplacedFactor)
                .displacedFactorSource(isLcaMode ? SOURCE_LIFECYCLE_IPCC : SOURCE_DIRECT_IPCC)
                .displacedFactorPublicationYear(isLcaMode ? YEAR_LIFECYCLE_IPCC : YEAR_DIRECT_IPCC)
                .displacedFactorBoundary(isLcaMode ? "LIFECYCLE_SCOPE_1_2_3" : "DIRECT_COMBUSTION_SCOPE_1")
                .displacedFactorRange("Sourced literature / thermal efficiency range")
                .displacedFactorDerivationMethod(isLcaMode ? "IPCC AR5 Table A.III.2" : "IPCC 2006 Stationary Combustion Tier 1")
                .displacedFactorHeatingValueBasis(isLcaMode ? "LCA deliverable basis" : "Net Calorific Value (NCV)")
                .replacementSource(replacementSource)
                .replacementGenerationTwh(totalDisplacedFossilTwh)
                .replacementGenerationGwh(totalDisplacedFossilTwh * 1000.0)
                .replacementEmissionFactorGco2PerKwh(activeReplacementFactor)
                .replacementFactorSource(isLcaMode ? SOURCE_LIFECYCLE_IPCC : "Zero Stack Emissions")
                .replacementFactorPublicationYear(isLcaMode ? YEAR_LIFECYCLE_IPCC : 2006)
                .replacementFactorBoundary(isLcaMode ? "LIFECYCLE_SCOPE_1_2_3" : "DIRECT_COMBUSTION_SCOPE_1")
                .displacementModelingType("Analytical Multi-Intervention Scenario Engine with Non-Negative and Minimum Residual Baseload Dispatch Constraints")
                .gwpConvention(isLcaMode ? "IPCC AR6 100-year GWP" : "IPCC 2006 CO2-only direct")
                .geographicAccountingScope(isLcaMode ? "Transnational cradle-to-grave lifecycle boundary" : "National territorial Scope 1")
                .dispatchEquivalenceCaveat("Assumes static marginal displacement up to available reducible fossil pool and minimum baseload constraints.")
                .documentedExclusions(exclusions)
                .customDisplacedFactor(first.getCustomDisplacedEmissionFactor() != null)
                .customDisplacedFactorSource(first.getCustomDisplacedFactorSource())
                .customReplacementFactor(first.getCustomReplacementEmissionFactor() != null)
                .customReplacementFactorSource(first.getCustomReplacementFactorSource())
                .configuredInterventions(interventions)
                .configuredConstraints(constraints)
                .dispatchConstraintDescription(dispatchConstraintDesc)
                .build();
    }

    private void validateRequest(DecarbonizationScenarioRequest req, CountryElectricityProfileDto profile) {
        if (req == null) {
            throw new SimulatorValidationException("Scenario request cannot be null.");
        }
        if (req.getCountryCode() == null || req.getCountryCode().isBlank()) {
            throw new SimulatorValidationException("Country code is required.");
        }

        // Validate constraints if present
        ScenarioConstraintsDto constraints = req.getConstraints();
        if (constraints != null) {
            double minCoalVal = 0.0;
            double minGasVal = 0.0;
            double minOilVal = 0.0;

            if (constraints.getMinResidualCoalTwh() != null) {
                if (!Double.isFinite(constraints.getMinResidualCoalTwh()) || constraints.getMinResidualCoalTwh() < 0) {
                    throw new SimulatorValidationException("Minimum residual Coal generation constraint cannot be negative and must be a finite number.");
                }
                if (constraints.getMinResidualCoalTwh() > profile.getCoalGenerationTwh() + 1e-6) {
                    throw new SimulatorValidationException(String.format(
                            "Minimum residual Coal constraint (%.2f TWh) exceeds baseline Coal generation for %s (%.2f TWh).",
                            constraints.getMinResidualCoalTwh(), profile.getCountryName(), profile.getCoalGenerationTwh()
                    ));
                }
                minCoalVal = constraints.getMinResidualCoalTwh();
            }
            if (constraints.getMinResidualGasTwh() != null) {
                if (!Double.isFinite(constraints.getMinResidualGasTwh()) || constraints.getMinResidualGasTwh() < 0) {
                    throw new SimulatorValidationException("Minimum residual Gas generation constraint cannot be negative and must be a finite number.");
                }
                if (constraints.getMinResidualGasTwh() > profile.getGasGenerationTwh() + 1e-6) {
                    throw new SimulatorValidationException(String.format(
                            "Minimum residual Gas constraint (%.2f TWh) exceeds baseline Gas generation for %s (%.2f TWh).",
                            constraints.getMinResidualGasTwh(), profile.getCountryName(), profile.getGasGenerationTwh()
                    ));
                }
                minGasVal = constraints.getMinResidualGasTwh();
            }
            if (constraints.getMinResidualOilTwh() != null) {
                if (!Double.isFinite(constraints.getMinResidualOilTwh()) || constraints.getMinResidualOilTwh() < 0) {
                    throw new SimulatorValidationException("Minimum residual Oil generation constraint cannot be negative and must be a finite number.");
                }
                if (constraints.getMinResidualOilTwh() > profile.getOilGenerationTwh() + 1e-6) {
                    throw new SimulatorValidationException(String.format(
                            "Minimum residual Oil constraint (%.2f TWh) exceeds baseline Oil generation for %s (%.2f TWh).",
                            constraints.getMinResidualOilTwh(), profile.getCountryName(), profile.getOilGenerationTwh()
                    ));
                }
                minOilVal = constraints.getMinResidualOilTwh();
            }
            if (constraints.getMinResidualFossilTwh() != null) {
                if (!Double.isFinite(constraints.getMinResidualFossilTwh()) || constraints.getMinResidualFossilTwh() < 0) {
                    throw new SimulatorValidationException("Minimum residual Fossil generation constraint cannot be negative and must be a finite number.");
                }
                if (constraints.getMinResidualFossilTwh() > profile.getTotalFossilGenerationTwh() + 1e-6) {
                    throw new SimulatorValidationException(String.format(
                            "Minimum residual Fossil constraint (%.2f TWh) exceeds baseline Fossil generation for %s (%.2f TWh).",
                            constraints.getMinResidualFossilTwh(), profile.getCountryName(), profile.getTotalFossilGenerationTwh()
                    ));
                }
                double sumFuelFloors = minCoalVal + minGasVal + minOilVal;
                if (constraints.getMinResidualFossilTwh() < sumFuelFloors - 1e-6) {
                    throw new SimulatorValidationException(String.format(
                            "Contradictory dispatch constraints: minimum residual total fossil floor (%.2f TWh) cannot be less than the sum of individual fuel floors (Coal: %.2f + Gas: %.2f + Oil: %.2f = %.2f TWh).",
                            constraints.getMinResidualFossilTwh(), minCoalVal, minGasVal, minOilVal, sumFuelFloors
                    ));
                }
            }
            double sumIndividualFloors = minCoalVal + minGasVal + minOilVal;
            if (sumIndividualFloors > profile.getTotalFossilGenerationTwh() + 1e-6) {
                throw new SimulatorValidationException(String.format(
                        "Contradictory dispatch constraints: sum of individual residual fuel floors (%.2f TWh) exceeds total baseline fossil generation for %s (%.2f TWh).",
                        sumIndividualFloors, profile.getCountryName(), profile.getTotalFossilGenerationTwh()
                ));
            }
            if (constraints.getMaxCleanAdditionLimitTwh() != null) {
                if (!Double.isFinite(constraints.getMaxCleanAdditionLimitTwh()) || constraints.getMaxCleanAdditionLimitTwh() < 0) {
                    throw new SimulatorValidationException("Maximum clean addition limit must be a non-negative finite number.");
                }
            }
        }

        // Validate interventions
        if (req.getInterventions() != null && !req.getInterventions().isEmpty()) {
            for (int i = 0; i < req.getInterventions().size(); i++) {
                ScenarioInterventionDto item = req.getInterventions().get(i);
                if (item.getCleanSource() == null || item.getCleanSource().isBlank()) {
                    throw new SimulatorValidationException("Clean source is required for intervention #" + (i + 1) + ".");
                }
                if (item.getTargetDisplacedFuel() == null || item.getTargetDisplacedFuel().isBlank()) {
                    throw new SimulatorValidationException("Target displaced fuel is required for intervention #" + (i + 1) + ".");
                }
                if (!Double.isFinite(item.getGenerationAmount()) || item.getGenerationAmount() <= 0) {
                    throw new SimulatorValidationException("Generation amount must be strictly positive (> 0) and finite for intervention #" + (i + 1) + ".");
                }
                String u = item.getGenerationUnit() != null ? item.getGenerationUnit().trim().toUpperCase() : "TWH";
                if (!"TWH".equals(u) && !"GWH".equals(u)) {
                    throw new SimulatorValidationException("Unsupported generation unit '" + item.getGenerationUnit() + "'. Supported units: ['TWH', 'GWH'].");
                }
                if (item.getCustomDisplacedEmissionFactor() != null && (!Double.isFinite(item.getCustomDisplacedEmissionFactor()) || item.getCustomDisplacedEmissionFactor() < 0)) {
                    throw new SimulatorValidationException("Custom displaced emission factor cannot be negative and must be a finite number.");
                }
                if (item.getCustomReplacementEmissionFactor() != null && (!Double.isFinite(item.getCustomReplacementEmissionFactor()) || item.getCustomReplacementEmissionFactor() < 0)) {
                    throw new SimulatorValidationException("Custom replacement clean emission factor cannot be negative and must be a finite number.");
                }
            }
        } else {
            // Legacy single-intervention validation
            if (req.getDisplacedFuel() == null || req.getDisplacedFuel().isBlank()) {
                throw new SimulatorValidationException("Displaced fuel is required. Supported: ['COAL', 'FOSSIL_GAS', 'OIL', 'GRID_FOSSIL_MIX'].");
            }
            if (req.getReplacementCleanSource() == null || req.getReplacementCleanSource().isBlank()) {
                throw new SimulatorValidationException("Replacement clean source is required. Supported: ['SOLAR_PV', 'WIND_ONSHORE', 'WIND_OFFSHORE', 'NUCLEAR', 'HYDRO'].");
            }
            if (!Double.isFinite(req.getGenerationAmount()) || req.getGenerationAmount() <= 0) {
                throw new SimulatorValidationException("Generation amount must be strictly positive (> 0) and finite. Received: " + req.getGenerationAmount());
            }
            if (req.getGenerationUnit() == null || req.getGenerationUnit().isBlank()) {
                throw new SimulatorValidationException("Generation unit is required. Supported: ['TWH', 'GWH'].");
            }
            String u = req.getGenerationUnit().trim().toUpperCase();
            if (!"TWH".equals(u) && !"GWH".equals(u)) {
                throw new SimulatorValidationException("Unsupported generation unit '" + req.getGenerationUnit() + "'. Supported units: ['TWH', 'GWH'].");
            }
            if (req.getCustomDisplacedEmissionFactor() != null && (!Double.isFinite(req.getCustomDisplacedEmissionFactor()) || req.getCustomDisplacedEmissionFactor() < 0)) {
                throw new SimulatorValidationException("Custom displaced emission factor cannot be negative and must be a finite number.");
            }
            if (req.getCustomReplacementEmissionFactor() != null && (!Double.isFinite(req.getCustomReplacementEmissionFactor()) || req.getCustomReplacementEmissionFactor() < 0)) {
                throw new SimulatorValidationException("Custom replacement clean emission factor cannot be negative and must be a finite number.");
            }
        }
    }

    public SensitivityIntervalDto calculateSensitivityInterval(
            CountryElectricityProfileDto profile,
            String displacedFuel,
            String replacementSource,
            double genGwh,
            boolean isLcaMode,
            DecarbonizationScenarioRequest req
    ) {
        Double customDisp = req != null ? req.getCustomDisplacedEmissionFactor() : null;
        Double customRep = req != null ? req.getCustomReplacementEmissionFactor() : null;

        double[] dispBounds = getDisplacedBounds(profile, displacedFuel, customDisp, isLcaMode);
        double[] repBounds = getReplacementBounds(replacementSource, customRep, isLcaMode);

        double rawMin = isLcaMode
                ? (genGwh * (dispBounds[0] - repBounds[2])) / 1_000_000.0
                : (genGwh * dispBounds[0]) / 1_000_000.0;
        double rawMed = isLcaMode
                ? (genGwh * (dispBounds[1] - repBounds[1])) / 1_000_000.0
                : (genGwh * dispBounds[1]) / 1_000_000.0;
        double rawMax = isLcaMode
                ? (genGwh * (dispBounds[2] - repBounds[0])) / 1_000_000.0
                : (genGwh * dispBounds[2]) / 1_000_000.0;

        double minAvoided = Math.min(rawMin, Math.min(rawMed, rawMax));
        double maxAvoided = Math.max(rawMax, Math.max(rawMed, rawMin));
        double medAvoided = rawMed;

        String description;
        String citation;
        int pubYear;

        if (isLcaMode) {
            pubYear = 2014;
            if ("OIL".equals(displacedFuel)) {
                description = "[PROVISIONAL / UNVERIFIED IN TABLE A.III.2] Dedicated oil-fired power lifecycle factor is not published in IPCC AR5 Table A.III.2; provisional peaker estimate (500–800 gCO2e/kWh) applied. Verified technologies (Coal, Gas, Solar, Wind, Nuclear, Hydro) reflect IPCC AR5 WGIII Table A.III.2 literature ranges.";
                citation = "Provisional Peaker Estimate & IPCC AR5 WGIII Annex III Table A.III.2 (Schlömer et al., 2014)";
            } else {
                description = "IPCC AR5 WGIII Annex III (Table A.III.2) literature harmonized lifecycle emission factor distribution (Schlömer et al., 2014). Represents global variation across published LCA studies in solar insolation, wind capacity factors, regional manufacturing grid carbon intensity, operational lifetimes, and upstream fuel methane fugitives.";
                citation = "IPCC AR5 WGIII Annex III Table A.III.2 (Lifecycle metrics in gCO2eq/kWh: literature min, 50th percentile median, literature max)";
            }
        } else {
            pubYear = 2006;
            description = "IPCC 2006 Guidelines for National Greenhouse Gas Inventories (Vol 2 Energy, Ch 2 Stationary Combustion Table 2.2). Represents power plant thermal conversion efficiency range: high efficiency (supercritical/advanced CCGT) to lower efficiency (aging subcritical/older CCGT).";
            citation = "Derived from IPCC 2006 Vol 2 Energy Table 2.2 stationary fuel combustion defaults divided by fleet thermal efficiency range.";
        }

        return SensitivityIntervalDto.builder()
                .minAvoidedEmissionsMt(minAvoided)
                .medianAvoidedEmissionsMt(medAvoided)
                .maxAvoidedEmissionsMt(maxAvoided)
                .displacedFactorMinGco2PerKwh(dispBounds[0])
                .displacedFactorMedianGco2PerKwh(dispBounds[1])
                .displacedFactorMaxGco2PerKwh(dispBounds[2])
                .replacementFactorMinGco2PerKwh(repBounds[0])
                .replacementFactorMedianGco2PerKwh(repBounds[1])
                .replacementFactorMaxGco2PerKwh(repBounds[2])
                .emissionFactorUnit(isLcaMode ? "gCO2e/kWh" : "gCO2/kWh")
                .emissionsUnit(isLcaMode ? "Mt CO2e/yr" : "Mt CO2/yr")
                .uncertaintyDescription(description)
                .sourceCitation(citation)
                .publicationYear(pubYear)
                .accountingBoundary(isLcaMode ? "LIFECYCLE_SCOPE_1_2_3" : "OPERATIONAL_SCOPE_1")
                .build();
    }

    public double getAvailableFossilGeneration(CountryElectricityProfileDto profile, String displacedFuel) {
        return switch (displacedFuel) {
            case "COAL" -> profile.getCoalGenerationTwh();
            case "FOSSIL_GAS", "GAS" -> profile.getGasGenerationTwh();
            case "OIL" -> profile.getOilGenerationTwh();
            case "GRID_FOSSIL_MIX" -> profile.getTotalFossilGenerationTwh();
            default -> throw new SimulatorValidationException(
                    "Unsupported displaced fuel '" + displacedFuel + "'. Supported: ['COAL', 'FOSSIL_GAS', 'OIL', 'GRID_FOSSIL_MIX']."
            );
        };
    }

    public String getFuelDisplayName(String fuel) {
        return switch (fuel) {
            case "COAL" -> "Coal";
            case "FOSSIL_GAS", "GAS" -> "Fossil Gas (CCGT)";
            case "OIL" -> "Oil / Diesel";
            case "GRID_FOSSIL_MIX" -> "Grid Fossil Mix";
            default -> fuel;
        };
    }

    public String getCleanDisplayName(String clean) {
        return switch (clean) {
            case "SOLAR_PV", "SOLAR" -> "Solar PV";
            case "WIND_ONSHORE" -> "Onshore Wind";
            case "WIND_OFFSHORE" -> "Offshore Wind";
            case "NUCLEAR" -> "Nuclear Power";
            case "HYDRO", "HYDROELECTRIC" -> "Hydroelectricity";
            default -> clean;
        };
    }
}
