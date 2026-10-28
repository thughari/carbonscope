package com.carbonscope.service;

import com.carbonscope.dto.simulator.*;
import com.carbonscope.exception.SimulatorValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DecarbonizationSimulatorServiceTest {

    private ReliableCarbonSourcesService reliableSourcesService;
    private DecarbonizationSimulatorService service;

    @BeforeEach
    void setUp() {
        reliableSourcesService = new ReliableCarbonSourcesService();
        service = new DecarbonizationSimulatorService(reliableSourcesService);
    }

    @Test
    @DisplayName("India 50 TWh Solar-for-Coal under Operational Boundary produces verified 47.50 Mt direct stack reduction")
    void testCalculationCorrectness_IndiaSolarDisplacingCoal_OperationalBoundary() {
        DecarbonizationScenarioRequest request = DecarbonizationScenarioRequest.builder()
                .countryCode("IND")
                .displacedFuel("COAL")
                .replacementCleanSource("SOLAR_PV")
                .generationAmount(50.0)
                .generationUnit("TWH")
                .accountingBoundary("OPERATIONAL_SCOPE_1")
                .build();

        DecarbonizationScenarioResponse response = service.calculateScenario(request);

        assertNotNull(response);
        assertEquals("IND", response.getCountryCode());
        assertEquals("India", response.getCountryName());
        assertEquals("OPERATIONAL_SCOPE_1", response.getAccountingBoundary());

        // Baseline verification against Ember yearly release (2023 snapshot 2024-05)
        DecarbonizationBaselineDto baseline = response.getBaseline();
        assertEquals(1918.26, baseline.getTotalGenerationTwh(), 0.001);
        assertEquals(1500.09, baseline.getFossilGenerationTwh(), 0.001);
        assertEquals(1445.44, baseline.getTargetFuelGenerationTwh(), 0.001);
        assertEquals(418.17, baseline.getCleanGenerationTwh(), 0.001);
        assertEquals(1369.09, baseline.getPowerSectorEmissionsMt(), 0.001);
        assertEquals(713.72, baseline.getGridCarbonIntensityGco2PerKwh(), 0.01);
        assertEquals("yearly_full_release_long_format.csv", baseline.getSourceFile());
        assertEquals("VERIFIED_SOURCE_OBSERVATION", baseline.getVerificationStatus());
        assertEquals(2023, baseline.getReportingYear());
        assertEquals("LIFECYCLE_GHG_CO2E", baseline.getAccountingBoundary());
        assertNotNull(baseline.getCleanTechnologiesBreakdown());

        // Operational calculations (direct combustion at stack)
        // 50 TWh = 50,000 GWh
        // Direct coal combustion derived factor = 950.0 g/kWh
        // Avoided direct stack emissions = 50,000 * 950 / 10^6 = 47.50 Mt CO2
        DecarbonizationImpactDto impact = response.getImpact();
        assertEquals(47.50, impact.getAvoidedDirectEmissionsMt(), 0.001);
        assertEquals(47.50, impact.getGrossAvoidedEmissionsMt(), 0.001);
        assertEquals(0.0, impact.getCleanReplacementLifecycleEmissionsMt(), 0.001,
                "Clean tech operational combustion emissions must be exactly 0.0 g/kWh");
        assertEquals(47.50, impact.getNetAvoidedEmissionsMt(), 0.001);

        // Physical generation metrics (TWh) are conserved and updated
        DecarbonizationResultsDto scenario = response.getScenario();
        assertEquals(1918.26, scenario.getTotalGenerationTwh(), 0.001);
        assertEquals(1450.09, scenario.getSimulatedFossilGenerationTwh(), 0.001);
        assertEquals(1395.44, scenario.getSimulatedTargetFuelGenerationTwh(), 0.001);
        assertEquals(468.17, scenario.getSimulatedCleanGenerationTwh(), 0.001);
        assertEquals(24.41, scenario.getSimulatedCleanPowerSharePct(), 0.01);

        // CRITICAL BOUNDARY ENFORCEMENT:
        // Ember baseline emissions are LIFECYCLE GHG (MtCO2e, IPCC AR5 Annex 3).
        // Direct operational Scope 1 avoided emissions CANNOT be subtracted from Ember's lifecycle baseline!
        // Therefore, simulated power sector emissions and simulated grid intensity MUST BE NULL (UNAVAILABLE)
        // to prevent invalid cross-boundary arithmetic.
        assertNull(scenario.getSimulatedPowerSectorEmissionsMt(),
                "Simulated direct power emissions must be null because Ember baseline is lifecycle CO2e");
        assertNull(scenario.getSimulatedGridCarbonIntensityGco2PerKwh(),
                "Simulated direct grid intensity must be null because Ember baseline is lifecycle CO2e");
        assertNull(scenario.getSimulatedNationalTotalEmissionsMt());
        assertNull(impact.getPowerSectorEmissionsReductionPct());
        assertNull(impact.getGridIntensityDeltaGco2PerKwh());
        assertNotNull(impact.getSimulatedMetricsUnavailableReason());
        assertTrue(impact.getSimulatedMetricsUnavailableReason().contains("UNAVAILABLE"));

        // Dual reporting: Life-cycle metrics are also preserved side-by-side without boundary pollution
        // Coal-PC median in Table A.III.2 is 820 gCO2eq/kWh -> 50,000 * 820 / 10^6 = 41.0 Mt
        assertEquals(41.00, impact.getGrossDisplacedLifecycleMt(), 0.001);
        assertEquals(2.40, impact.getCleanEmbodiedLifecycleMt(), 0.001);
        assertEquals(38.60, impact.getNetAvoidedLifecycleMt(), 0.001);
        assertFalse(impact.isNetNegativeAbatement());

        // Documented derivation assumptions
        ScenarioAssumptionsDto assumptions = response.getAssumptions();
        assertNotNull(assumptions.getDisplacedFactorDerivationMethod());
        assertTrue(assumptions.getDisplacedFactorDerivationMethod().contains("IPCC 2006"));
        assertTrue(assumptions.getDisplacedFactorDerivationMethod().contains("0.0036"));
        assertNotNull(assumptions.getDisplacedFactorHeatingValueBasis());
        assertTrue(assumptions.getDisplacedFactorHeatingValueBasis().contains("Net Calorific Value"));
        assertEquals("Analytical 1:1 Static Energy Scenario (Assumption). Does not represent dynamic dispatch, merit-order market equilibrium, curtailment, or grid transmission/storage losses.",
                assumptions.getDisplacementModelingType());
    }

    @Test
    @DisplayName("India 50 TWh Solar-for-Coal under Life-Cycle Boundary produces 38.60 Mt global net atmospheric abatement using IPCC AR5 Table A.III.2 factors")
    void testCalculationCorrectness_IndiaSolarDisplacingCoal_LifecycleBoundary() {
        DecarbonizationScenarioRequest request = DecarbonizationScenarioRequest.builder()
                .countryCode("IND")
                .displacedFuel("COAL")
                .replacementCleanSource("SOLAR_PV")
                .generationAmount(50.0)
                .generationUnit("TWH")
                .accountingBoundary("LIFECYCLE_SCOPE_1_2_3")
                .build();

        DecarbonizationScenarioResponse response = service.calculateScenario(request);

        assertNotNull(response);
        assertEquals("LIFECYCLE_SCOPE_1_2_3", response.getAccountingBoundary());

        DecarbonizationImpactDto impact = response.getImpact();
        // Gross lifecycle = 50,000 * 820 / 10^6 = 41.0 Mt CO2e
        assertEquals(41.00, impact.getGrossAvoidedEmissionsMt(), 0.001);
        // Solar embodied lifecycle = 50,000 * 48 / 10^6 = 2.4 Mt CO2e
        assertEquals(2.40, impact.getCleanReplacementLifecycleEmissionsMt(), 0.001);
        // Net lifecycle avoided = 41.0 - 2.4 = 38.60 Mt CO2e
        assertEquals(38.60, impact.getNetAvoidedEmissionsMt(), 0.001);
        assertEquals(38_600_000.0, impact.getNetAvoidedEmissionsTonnes(), 0.1);

        // In Lifecycle mode, national post-intervention inventory metrics are DISABLED (null)
        // because transnational clean supply chain emissions cannot be subtracted from a territorial baseline.
        DecarbonizationResultsDto scenario = response.getScenario();
        assertNull(scenario.getSimulatedPowerSectorEmissionsMt(),
                "Simulated power sector emissions must be null to prevent cross-boundary baseline subtraction");
        assertNull(scenario.getSimulatedGridCarbonIntensityGco2PerKwh(),
                "Simulated grid intensity must be null to prevent cross-boundary baseline subtraction");
        assertNull(scenario.getSimulatedNationalTotalEmissionsMt(),
                "Simulated national emissions must be null");
        assertNull(impact.getPowerSectorEmissionsReductionPct());
        assertNull(impact.getGridIntensityDeltaGco2PerKwh());
        assertNotNull(impact.getSimulatedMetricsUnavailableReason());
        assertTrue(impact.getSimulatedMetricsUnavailableReason().contains("UNAVAILABLE"));
    }

    @Test
    @DisplayName("Regression Test: Exact baseline values against versioned, traceable Ember CSV fixture")
    void testExactBaselineValues_AgainstVersionedTraceableFixture() {
        // China (CHN)
        CountryElectricityProfileDto chn = service.getCountryProfile("CHN");
        assertEquals(9456.45, chn.getTotalGenerationTwh(), 0.01);
        assertEquals(5752.74, chn.getCoalGenerationTwh(), 0.01);
        assertEquals(297.80, chn.getGasGenerationTwh(), 0.01);
        assertEquals(76.97, chn.getOilGenerationTwh(), 0.01);
        assertEquals(6127.51, chn.getTotalFossilGenerationTwh(), 0.01);
        assertEquals(3328.94, chn.getCleanGenerationTwh(), 0.01);
        assertEquals(5515.45, chn.getPowerSectorEmissionsMt(), 0.01);
        assertEquals(583.25, chn.getGridCarbonIntensityGco2PerKwh(), 0.01);
        assertEquals("yearly_full_release_long_format.csv", chn.getSourceFile());
        assertEquals("VERIFIED_SOURCE_OBSERVATION", chn.getVerificationStatus());
        assertEquals(2023, chn.getBaselineYear());
        assertEquals("LIFECYCLE_GHG_CO2E", chn.getAccountingBoundary());

        // United States (USA)
        CountryElectricityProfileDto usa = service.getCountryProfile("USA");
        assertEquals(4253.91, usa.getTotalGenerationTwh(), 0.01);
        assertEquals(675.11, usa.getCoalGenerationTwh(), 0.01);
        assertEquals(1806.06, usa.getGasGenerationTwh(), 0.01);
        assertEquals(33.13, usa.getOilGenerationTwh(), 0.01);
        assertEquals(2514.30, usa.getTotalFossilGenerationTwh(), 0.01);
        assertEquals(1739.61, usa.getCleanGenerationTwh(), 0.01);
        assertEquals(1671.37, usa.getPowerSectorEmissionsMt(), 0.01);
        assertEquals(392.90, usa.getGridCarbonIntensityGco2PerKwh(), 0.01);

        // Germany (DEU)
        CountryElectricityProfileDto deu = service.getCountryProfile("DEU");
        assertEquals(506.72, deu.getTotalGenerationTwh(), 0.01);
        assertEquals(124.78, deu.getCoalGenerationTwh(), 0.01);
        assertEquals(76.66, deu.getGasGenerationTwh(), 0.01);
        assertEquals(20.96, deu.getOilGenerationTwh(), 0.01);
        assertEquals(222.40, deu.getTotalFossilGenerationTwh(), 0.01);
        assertEquals(284.32, deu.getCleanGenerationTwh(), 0.01);
        assertEquals(184.24, deu.getPowerSectorEmissionsMt(), 0.01);
        assertEquals(363.59, deu.getGridCarbonIntensityGco2PerKwh(), 0.01);
    }

    @Test
    @DisplayName("Regression Test: Category reconciliation and source-year consistency across all 16 countries")
    void testCategoryReconciliationAndSourceYearConsistency() {
        List<CountryElectricityProfileDto> countries = service.getSupportedCountries();
        assertEquals(16, countries.size(), "Must have exactly 16 verified countries");

        for (CountryElectricityProfileDto c : countries) {
            assertEquals(2023, c.getBaselineYear(), c.getCountryCode() + ": Source year must be 2023");
            assertEquals("yearly_full_release_long_format.csv", c.getSourceFile(), c.getCountryCode() + ": Source file");
            assertEquals("VERIFIED_SOURCE_OBSERVATION", c.getVerificationStatus(), c.getCountryCode() + ": Verification status");
            assertEquals("LIFECYCLE_GHG_CO2E", c.getAccountingBoundary(), c.getCountryCode() + ": Accounting boundary");
            assertTrue(c.getReconciliationStatus().contains("INTERNAL_CATEGORY_RECONCILED"));
            assertNotNull(c.getGenerationAccountingConvention());

            double sumFossil = c.getCoalGenerationTwh() + c.getGasGenerationTwh() + c.getOilGenerationTwh();
            assertEquals(c.getTotalFossilGenerationTwh(), sumFossil, 0.01,
                    c.getCountryCode() + ": Coal + Gas + Oil must equal Total Fossil");

            double totalReconciled = c.getTotalFossilGenerationTwh() + c.getCleanGenerationTwh();
            assertEquals(c.getTotalGenerationTwh(), totalReconciled, 0.01,
                    c.getCountryCode() + ": Total Fossil + Clean must equal Total Generation");

            double cleanPct = (c.getCleanGenerationTwh() / c.getTotalGenerationTwh()) * 100.0;
            assertEquals(c.getCleanPowerSharePct(), cleanPct, 0.02,
                    c.getCountryCode() + ": Clean power share must equal (Clean / Total) * 100");

            double expectedIntensity = (c.getPowerSectorEmissionsMt() * 1000.0) / c.getTotalGenerationTwh();
            assertEquals(c.getGridCarbonIntensityGco2PerKwh(), expectedIntensity, 0.05,
                    c.getCountryCode() + ": Grid intensity must mathematically equal (Emissions Mt * 1000) / Generation TWh");
        }
    }

    @Test
    @DisplayName("Regression Test: IPCC Table 2.2 fuel-to-electricity factor conversion formula EF_electric = EF_fuel(kg/TJ) * 0.0036 / efficiency")
    void testFuelFactorConversionFormula_FactorOf1000Regression() {
        // Exact IPCC 2006 Vol 2 Ch 2 Table 2.2 Default Factors in kg CO2 / TJ (NCV basis):
        double ipccCoalFuelFactorKgPerTj = 95_000.0; // Table 2.2 'Other Bituminous Coal' default
        double ipccGasFuelFactorKgPerTj = 56_100.0;  // Table 2.2 'Natural Gas' default
        double ipccOilFuelFactorKgPerTj = 77_400.0;  // Table 2.2 'Residual Fuel Oil' default

        double coalEfficiency = 0.360;  // 36.0% fleet thermal efficiency
        double gasEfficiency = 0.4925;  // 49.25% CCGT fleet thermal efficiency
        double oilEfficiency = 0.3870;  // 38.70% peaker fleet thermal efficiency

        // Verified Conversion Formula: EF_electric (g/kWh) = EF_fuel (kg/TJ) * 0.0036 / efficiency
        double derivedCoalFactor = (ipccCoalFuelFactorKgPerTj * 0.0036) / coalEfficiency;
        double derivedGasFactor = (ipccGasFuelFactorKgPerTj * 0.0036) / gasEfficiency;
        double derivedOilFactor = (ipccOilFuelFactorKgPerTj * 0.0036) / oilEfficiency;

        assertEquals(950.0, derivedCoalFactor, 0.001);
        assertEquals(410.07, derivedGasFactor, 0.01);
        assertEquals(720.0, derivedOilFactor, 0.001);

        assertEquals(DecarbonizationSimulatorService.EF_COAL_DIRECT, derivedCoalFactor, 0.001);
        assertEquals(DecarbonizationSimulatorService.EF_FOSSIL_GAS_DIRECT, derivedGasFactor, 0.1);
        assertEquals(DecarbonizationSimulatorService.EF_OIL_DIRECT, derivedOilFactor, 0.001);

        // Dimensional Regression Check: Fails if 1,000x error (using 3.6 instead of 0.0036) were applied
        double erroneousCoalFactorWith3Point6 = (ipccCoalFuelFactorKgPerTj * 3.6) / coalEfficiency;
        assertEquals(950_000.0, erroneousCoalFactorWith3Point6, 0.001);
        assertNotEquals(DecarbonizationSimulatorService.EF_COAL_DIRECT, erroneousCoalFactorWith3Point6);
        assertEquals(1000.0, erroneousCoalFactorWith3Point6 / derivedCoalFactor, 0.001,
                "Erroneous formula with 3.6 is exactly 1,000x too large compared to verified 0.0036 formula");
    }

    @Test
    @DisplayName("Regression Test: Unit conversions between TWh and GWh consistency")
    void testUnitConversions_TwhVsGwhConsistency() {
        DecarbonizationScenarioRequest twhReq = DecarbonizationScenarioRequest.builder()
                .countryCode("IND")
                .displacedFuel("COAL")
                .replacementCleanSource("SOLAR_PV")
                .generationAmount(50.0)
                .generationUnit("TWH")
                .accountingBoundary("OPERATIONAL_SCOPE_1")
                .build();

        DecarbonizationScenarioRequest gwhReq = DecarbonizationScenarioRequest.builder()
                .countryCode("IND")
                .displacedFuel("COAL")
                .replacementCleanSource("SOLAR_PV")
                .generationAmount(50_000.0)
                .generationUnit("GWH")
                .accountingBoundary("OPERATIONAL_SCOPE_1")
                .build();

        DecarbonizationScenarioResponse twhRes = service.calculateScenario(twhReq);
        DecarbonizationScenarioResponse gwhRes = service.calculateScenario(gwhReq);

        assertEquals(twhRes.getImpact().getGrossAvoidedEmissionsMt(), gwhRes.getImpact().getGrossAvoidedEmissionsMt(), 0.0001);
        assertEquals(twhRes.getImpact().getNetAvoidedEmissionsMt(), gwhRes.getImpact().getNetAvoidedEmissionsMt(), 0.0001);
        assertEquals(twhRes.getScenario().getTotalGenerationTwh(), gwhRes.getScenario().getTotalGenerationTwh(), 0.0001);
    }

    @Test
    @DisplayName("Regression Test: Incompatible emissions boundaries are rejected or kept strictly separated")
    void testIncompatibleEmissionsBoundaries() {
        // Unknown boundary rejected
        DecarbonizationScenarioRequest request = DecarbonizationScenarioRequest.builder()
                .countryCode("IND")
                .displacedFuel("COAL")
                .replacementCleanSource("SOLAR_PV")
                .generationAmount(10.0)
                .generationUnit("TWH")
                .accountingBoundary("INVENTED_HYBRID_SCOPE")
                .build();

        SimulatorValidationException ex = assertThrows(SimulatorValidationException.class, () ->
                service.calculateScenario(request));
        assertTrue(ex.getMessage().contains("Unsupported accounting boundary"));

        // Operational boundary strictly outputs 0 for clean technology stack emissions
        DecarbonizationScenarioRequest opReq = DecarbonizationScenarioRequest.builder()
                .countryCode("USA")
                .displacedFuel("COAL")
                .replacementCleanSource("SOLAR_PV")
                .generationAmount(10.0)
                .generationUnit("TWH")
                .accountingBoundary("OPERATIONAL_SCOPE_1")
                .build();

        DecarbonizationScenarioResponse opRes = service.calculateScenario(opReq);
        assertEquals(0.0, opRes.getImpact().getCleanReplacementLifecycleEmissionsMt(), 0.0,
                "Operational Scope 1 must not pollute direct stack accounts with lifecycle embodied emissions");
    }

    @Test
    @DisplayName("Regression Test: Unverified baseline data and unsupported countries are rejected")
    void testUnverifiedBaselineData_Rejection() {
        DecarbonizationScenarioRequest request = DecarbonizationScenarioRequest.builder()
                .countryCode("ZZZ")
                .displacedFuel("COAL")
                .replacementCleanSource("SOLAR_PV")
                .generationAmount(10.0)
                .generationUnit("TWH")
                .build();

        SimulatorValidationException ex = assertThrows(SimulatorValidationException.class, () ->
                service.calculateScenario(request));

        assertTrue(ex.getMessage().contains("not supported"));
        assertTrue(ex.getMessage().contains("IND"));
    }

    @Test
    @DisplayName("Regression Test: Invalid or unsupported factors are rejected")
    void testInvalidOrUnsupportedFactors() {
        // Negative factor rejected
        DecarbonizationScenarioRequest negFactorReq = DecarbonizationScenarioRequest.builder()
                .countryCode("IND")
                .displacedFuel("COAL")
                .replacementCleanSource("SOLAR_PV")
                .generationAmount(10.0)
                .generationUnit("TWH")
                .customDisplacedEmissionFactor(-50.0)
                .build();

        SimulatorValidationException ex = assertThrows(SimulatorValidationException.class, () ->
                service.calculateScenario(negFactorReq));
        assertTrue(ex.getMessage().contains("cannot be negative"));

        // Unsupported fuel rejected
        DecarbonizationScenarioRequest invalidFuelReq = DecarbonizationScenarioRequest.builder()
                .countryCode("IND")
                .displacedFuel("PEAT_MOSS")
                .replacementCleanSource("SOLAR_PV")
                .generationAmount(10.0)
                .generationUnit("TWH")
                .build();

        SimulatorValidationException ex2 = assertThrows(SimulatorValidationException.class, () ->
                service.calculateScenario(invalidFuelReq));
        assertTrue(ex2.getMessage().contains("Unsupported displaced fuel"));
    }

    @Test
    @DisplayName("Regression Test: Negative abatement detection and physical displacement limits")
    void testNegativeAbatementAndDisplacementLimits() {
        // Exceeding available generation (GBR has 3.78 TWh coal)
        DecarbonizationScenarioRequest exceedReq = DecarbonizationScenarioRequest.builder()
                .countryCode("GBR")
                .displacedFuel("COAL")
                .replacementCleanSource("WIND_OFFSHORE")
                .generationAmount(5.0)
                .generationUnit("TWH")
                .build();

        SimulatorValidationException ex = assertThrows(SimulatorValidationException.class, () ->
                service.calculateScenario(exceedReq));
        assertTrue(ex.getMessage().contains("exceeds maximum available Coal generation"));
        assertTrue(ex.getMessage().contains("3.78 TWh"));

        // Displacing zero-generation fuel (South Africa has 0.0 TWh gas)
        DecarbonizationScenarioRequest zeroReq = DecarbonizationScenarioRequest.builder()
                .countryCode("ZAF")
                .displacedFuel("FOSSIL_GAS")
                .replacementCleanSource("SOLAR_PV")
                .generationAmount(1.0)
                .generationUnit("TWH")
                .build();

        SimulatorValidationException ex2 = assertThrows(SimulatorValidationException.class, () ->
                service.calculateScenario(zeroReq));
        assertTrue(ex2.getMessage().contains("exceeds maximum available Fossil Gas (CCGT) generation for South Africa (0.00 TWh)"));

        // Negative abatement
        DecarbonizationScenarioRequest negAbateReq = DecarbonizationScenarioRequest.builder()
                .countryCode("USA")
                .displacedFuel("FOSSIL_GAS") // lifecycle 490 g/kWh
                .replacementCleanSource("SOLAR_PV")
                .generationAmount(10.0)
                .generationUnit("TWH")
                .accountingBoundary("LIFECYCLE_SCOPE_1_2_3")
                .customReplacementEmissionFactor(600.0)
                .build();

        DecarbonizationScenarioResponse negRes = service.calculateScenario(negAbateReq);
        assertTrue(negRes.getImpact().isNetNegativeAbatement());
        assertEquals(-1.10, negRes.getImpact().getNetAvoidedEmissionsMt(), 0.001);
        assertNotNull(negRes.getImpact().getNetAbatementWarning());
    }

    @Test
    @DisplayName("Regression Test: Lifecycle mode leaves post-scenario national inventory metrics strictly null")
    void testLifecycleMode_DerivedNationalInventoryMetricsAreNullAndUnavailable() {
        DecarbonizationScenarioRequest req = DecarbonizationScenarioRequest.builder()
                .countryCode("IND")
                .displacedFuel("COAL")
                .replacementCleanSource("SOLAR_PV")
                .generationAmount(50.0)
                .generationUnit("TWH")
                .accountingBoundary("LIFECYCLE_SCOPE_1_2_3")
                .build();

        DecarbonizationScenarioResponse res = service.calculateScenario(req);

        // Valid scenario output:
        // 50,000 GWh * (820 - 48) / 10^6 = 38.60 Mt
        assertEquals(38.60, res.getImpact().getNetAvoidedEmissionsMt(), 0.001);

        // Disabled national inventory metrics:
        assertNull(res.getScenario().getSimulatedPowerSectorEmissionsMt(),
                "simulatedPowerSectorEmissionsMt must be null in lifecycle mode");
        assertNull(res.getScenario().getSimulatedGridCarbonIntensityGco2PerKwh(),
                "simulatedGridCarbonIntensityGco2PerKwh must be null in lifecycle mode");
        assertNull(res.getScenario().getSimulatedNationalTotalEmissionsMt(),
                "simulatedNationalTotalEmissionsMt must be null in lifecycle mode");
        assertNull(res.getImpact().getPowerSectorEmissionsReductionPct(),
                "powerSectorEmissionsReductionPct must be null in lifecycle mode");
        assertNull(res.getImpact().getNationalEmissionsReductionPct(),
                "nationalEmissionsReductionPct must be null in lifecycle mode");
        assertNull(res.getImpact().getGridIntensityDeltaGco2PerKwh(),
                "gridIntensityDeltaGco2PerKwh must be null in lifecycle mode");

        // Explanatory metadata:
        assertNotNull(res.getImpact().getSimulatedMetricsUnavailableReason());
        assertTrue(res.getImpact().getSimulatedMetricsUnavailableReason().contains("DISABLED and UNAVAILABLE"));
        assertTrue(res.getImpact().getSimulatedMetricsUnavailableReason().contains("transnational supply chains"));
    }

    @Test
    @DisplayName("Regression Test: Operational mode leaves post-scenario national inventory metrics strictly null")
    void testOperationalMode_DerivedNationalInventoryMetricsAreNullAndUnavailable() {
        DecarbonizationScenarioRequest req = DecarbonizationScenarioRequest.builder()
                .countryCode("IND")
                .displacedFuel("COAL")
                .replacementCleanSource("SOLAR_PV")
                .generationAmount(50.0)
                .generationUnit("TWH")
                .accountingBoundary("OPERATIONAL_SCOPE_1")
                .build();

        DecarbonizationScenarioResponse res = service.calculateScenario(req);

        // Valid conditional output:
        assertEquals(47.50, res.getImpact().getGrossAvoidedEmissionsMt(), 0.001);
        assertEquals(47.50, res.getImpact().getNetAvoidedEmissionsMt(), 0.001);

        // Disabled national inventory metrics:
        assertNull(res.getScenario().getSimulatedPowerSectorEmissionsMt(),
                "simulatedPowerSectorEmissionsMt must be null in operational mode");
        assertNull(res.getScenario().getSimulatedGridCarbonIntensityGco2PerKwh(),
                "simulatedGridCarbonIntensityGco2PerKwh must be null in operational mode");
        assertNull(res.getImpact().getPowerSectorEmissionsReductionPct());

        // Explanatory metadata:
        assertNotNull(res.getImpact().getSimulatedMetricsUnavailableReason());
        assertTrue(res.getImpact().getSimulatedMetricsUnavailableReason().contains("UNAVAILABLE"));
        assertTrue(res.getImpact().getSimulatedMetricsUnavailableReason().contains("lifecycle GHG emissions"));
    }

    @Test
    @DisplayName("Regression Test: Scenario assumptions contain documented exclusions and boundary metadata")
    void testScenarioAssumptions_ContainExclusionsAndBoundaries() {
        DecarbonizationScenarioRequest req = DecarbonizationScenarioRequest.builder()
                .countryCode("IND")
                .displacedFuel("COAL")
                .replacementCleanSource("SOLAR_PV")
                .generationAmount(50.0)
                .generationUnit("TWH")
                .accountingBoundary("LIFECYCLE_SCOPE_1_2_3")
                .build();

        DecarbonizationScenarioResponse res = service.calculateScenario(req);
        ScenarioAssumptionsDto assumptions = res.getAssumptions();

        assertNotNull(assumptions.getDocumentedExclusions());
        assertTrue(assumptions.getDocumentedExclusions().size() >= 6, "Must document at least 6 explicit exclusions");

        boolean hasStorage = assumptions.getDocumentedExclusions().stream().anyMatch(e -> e.contains("Energy Storage Excluded"));
        boolean hasTiming = assumptions.getDocumentedExclusions().stream().anyMatch(e -> e.contains("Diurnal & Seasonal Timing Excluded"));
        boolean hasRamping = assumptions.getDocumentedExclusions().stream().anyMatch(e -> e.contains("Thermal Ramping & Minimum Plant Loading Excluded"));
        boolean hasLosses = assumptions.getDocumentedExclusions().stream().anyMatch(e -> e.contains("Grid Transmission Losses & Bottlenecks Excluded"));
        boolean hasDemandGrowth = assumptions.getDocumentedExclusions().stream().anyMatch(e -> e.contains("Demand Load Growth Excluded"));

        assertTrue(hasStorage, "Must explicitly exclude storage");
        assertTrue(hasTiming, "Must explicitly exclude diurnal timing");
        assertTrue(hasRamping, "Must explicitly exclude ramping constraints");
        assertTrue(hasLosses, "Must explicitly exclude transmission losses");
        assertTrue(hasDemandGrowth, "Must explicitly exclude demand growth");

        assertNotNull(assumptions.getGwpConvention());
        assertTrue(assumptions.getGwpConvention().contains("IPCC AR6"));
        assertNotNull(assumptions.getGeographicAccountingScope());
        assertTrue(assumptions.getGeographicAccountingScope().contains("Transnational"));
        assertNotNull(assumptions.getDispatchEquivalenceCaveat());
    }

    @Test
    @DisplayName("Regression Test: Provenance metadata distinguishes published source vs derived metrics")
    void testProvenance_DistinguishesSourceAndDerivedMetadata() {
        CountryElectricityProfileDto profile = service.getCountryProfile("IND");

        assertEquals("2024-05-15", profile.getDatasetSnapshotDate());
        assertEquals("https://ember-climate.org/creative-commons/data-methodology/", profile.getMethodologyUrl());
        assertNotNull(profile.getSourcePublishedMetrics());
        assertTrue(profile.getSourcePublishedMetrics().contains("Total Generation (TWh)"));
        assertTrue(profile.getSourcePublishedMetrics().contains("Power Sector Emissions (mtCO2)"));

        assertNotNull(profile.getDerivedMetrics());
        assertTrue(profile.getDerivedMetrics().contains("Total Fossil Generation"));
        assertTrue(profile.getDerivedMetrics().contains("Clean Power Share %"));
        assertTrue(profile.getDerivedMetrics().contains("Grid Carbon Intensity"));

        DecarbonizationScenarioRequest req = DecarbonizationScenarioRequest.builder()
                .countryCode("IND")
                .displacedFuel("COAL")
                .replacementCleanSource("SOLAR_PV")
                .generationAmount(10.0)
                .generationUnit("TWH")
                .build();

        DecarbonizationScenarioResponse res = service.calculateScenario(req);
        DecarbonizationBaselineDto baseline = res.getBaseline();

        assertEquals("2024-05-15", baseline.getDatasetSnapshotDate());
        assertEquals("https://ember-climate.org/creative-commons/data-methodology/", baseline.getMethodologyUrl());
        assertEquals(profile.getSourcePublishedMetrics(), baseline.getSourcePublishedMetrics());
        assertEquals(profile.getDerivedMetrics(), baseline.getDerivedMetrics());
    }

    @Test
    @DisplayName("Sensitivity Interval (Lifecycle): 50 TWh Solar-for-Coal produces mathematically ordered IPCC AR5 Table A.III.2 bounds")
    void testSensitivityInterval_Lifecycle_IndiaCoalSolar_MathematicalOrderingAndAR5Bounds() {
        DecarbonizationScenarioRequest req = DecarbonizationScenarioRequest.builder()
                .countryCode("IND")
                .displacedFuel("COAL")
                .replacementCleanSource("SOLAR_PV")
                .generationAmount(50.0)
                .generationUnit("TWH")
                .accountingBoundary("LIFECYCLE_SCOPE_1_2_3")
                .build();

        DecarbonizationScenarioResponse res = service.calculateScenario(req);
        SensitivityIntervalDto interval = res.getImpact().getSensitivityInterval();

        assertNotNull(interval, "Sensitivity interval must be populated");
        
        // Exact mathematical checks:
        // Lower: 50,000 GWh * (740 - 180) / 10^6 = 28.00 Mt
        // Median: 50,000 GWh * (820 - 48) / 10^6 = 38.60 Mt
        // Upper: 50,000 GWh * (910 - 18) / 10^6 = 44.60 Mt
        assertEquals(28.00, interval.getMinAvoidedEmissionsMt(), 0.001);
        assertEquals(38.60, interval.getMedianAvoidedEmissionsMt(), 0.001);
        assertEquals(44.60, interval.getMaxAvoidedEmissionsMt(), 0.001);

        // Strict monotonicity guarantee
        assertTrue(interval.getMinAvoidedEmissionsMt() <= interval.getMedianAvoidedEmissionsMt(),
                "Min bound must be <= median");
        assertTrue(interval.getMedianAvoidedEmissionsMt() <= interval.getMaxAvoidedEmissionsMt(),
                "Median must be <= max bound");

        // Factor ranges check (IPCC AR5 Table A.III.2 Coal - PC & Solar PV utility)
        assertEquals(740.0, interval.getDisplacedFactorMinGco2PerKwh(), 0.001);
        assertEquals(820.0, interval.getDisplacedFactorMedianGco2PerKwh(), 0.001);
        assertEquals(910.0, interval.getDisplacedFactorMaxGco2PerKwh(), 0.001);

        assertEquals(18.0, interval.getReplacementFactorMinGco2PerKwh(), 0.001);
        assertEquals(48.0, interval.getReplacementFactorMedianGco2PerKwh(), 0.001);
        assertEquals(180.0, interval.getReplacementFactorMaxGco2PerKwh(), 0.001);

        assertEquals("gCO2e/kWh", interval.getEmissionFactorUnit());
        assertEquals("Mt CO2e/yr", interval.getEmissionsUnit());
        assertEquals("LIFECYCLE_SCOPE_1_2_3", interval.getAccountingBoundary());
        assertEquals(2014, interval.getPublicationYear());
        assertNotNull(interval.getUncertaintyDescription());
        assertNotNull(interval.getSourceCitation());
        assertTrue(interval.getSourceCitation().contains("IPCC AR5"));
    }

    @Test
    @DisplayName("Sensitivity Interval (Operational): 50 TWh Solar-for-Coal produces mathematically ordered thermal efficiency bounds")
    void testSensitivityInterval_Operational_IndiaCoalSolar_CombustionEfficiencyRange() {
        DecarbonizationScenarioRequest req = DecarbonizationScenarioRequest.builder()
                .countryCode("IND")
                .displacedFuel("COAL")
                .replacementCleanSource("SOLAR_PV")
                .generationAmount(50.0)
                .generationUnit("TWH")
                .accountingBoundary("OPERATIONAL_SCOPE_1")
                .build();

        DecarbonizationScenarioResponse res = service.calculateScenario(req);
        SensitivityIntervalDto interval = res.getImpact().getSensitivityInterval();

        assertNotNull(interval);

        // Operational efficiency bounds:
        // 50,000 GWh * 855.0 / 10^6 = 42.75 Mt
        // 50,000 GWh * 950.0 / 10^6 = 47.50 Mt
        // 50,000 GWh * 1036.36 / 10^6 = 51.818 Mt
        assertEquals(42.75, interval.getMinAvoidedEmissionsMt(), 0.01);
        assertEquals(47.50, interval.getMedianAvoidedEmissionsMt(), 0.01);
        assertEquals(51.82, interval.getMaxAvoidedEmissionsMt(), 0.02);

        // Strict monotonicity
        assertTrue(interval.getMinAvoidedEmissionsMt() <= interval.getMedianAvoidedEmissionsMt());
        assertTrue(interval.getMedianAvoidedEmissionsMt() <= interval.getMaxAvoidedEmissionsMt());

        // Zero direct combustion for clean replacement
        assertEquals(0.0, interval.getReplacementFactorMinGco2PerKwh(), 0.001);
        assertEquals(0.0, interval.getReplacementFactorMedianGco2PerKwh(), 0.001);
        assertEquals(0.0, interval.getReplacementFactorMaxGco2PerKwh(), 0.001);

        assertEquals("gCO2/kWh", interval.getEmissionFactorUnit());
        assertEquals("Mt CO2/yr", interval.getEmissionsUnit());
        assertEquals("OPERATIONAL_SCOPE_1", interval.getAccountingBoundary());
        assertEquals(2006, interval.getPublicationYear());
    }

    @Test
    @DisplayName("Sensitivity Interval: Custom override safely defines interval bounds without errors")
    void testSensitivityInterval_CustomFactorOverride_SafelyHandled() {
        DecarbonizationScenarioRequest req = DecarbonizationScenarioRequest.builder()
                .countryCode("IND")
                .displacedFuel("COAL")
                .replacementCleanSource("SOLAR_PV")
                .generationAmount(20.0)
                .generationUnit("TWH")
                .accountingBoundary("LIFECYCLE_SCOPE_1_2_3")
                .customDisplacedEmissionFactor(900.0)
                .customDisplacedFactorSource("CEA India 2024 Thermal Fleet Study")
                .build();

        DecarbonizationScenarioResponse res = service.calculateScenario(req);
        SensitivityIntervalDto interval = res.getImpact().getSensitivityInterval();

        assertNotNull(interval);
        assertEquals(900.0, interval.getDisplacedFactorMinGco2PerKwh(), 0.001);
        assertEquals(900.0, interval.getDisplacedFactorMedianGco2PerKwh(), 0.001);
        assertEquals(900.0, interval.getDisplacedFactorMaxGco2PerKwh(), 0.001);

        // Replacement uses IPCC AR5 Table A.III.2 range [18, 48, 180]
        // 20,000 GWh * (900 - 180) / 10^6 = 14.40 Mt
        // 20,000 GWh * (900 - 48) / 10^6 = 17.04 Mt
        // 20,000 GWh * (900 - 18) / 10^6 = 17.64 Mt
        assertEquals(14.40, interval.getMinAvoidedEmissionsMt(), 0.001);
        assertEquals(17.04, interval.getMedianAvoidedEmissionsMt(), 0.001);
        assertEquals(17.64, interval.getMaxAvoidedEmissionsMt(), 0.001);

        assertTrue(interval.getMinAvoidedEmissionsMt() <= interval.getMedianAvoidedEmissionsMt());
        assertTrue(interval.getMedianAvoidedEmissionsMt() <= interval.getMaxAvoidedEmissionsMt());
    }

    @Test
    @DisplayName("Sensitivity Interval: Monotonicity holds across all supported clean technologies")
    void testSensitivityInterval_AllCleanTechnologies_MonotonicOrdering() {
        List<String> cleanTechs = List.of("SOLAR_PV", "WIND_ONSHORE", "WIND_OFFSHORE", "NUCLEAR", "HYDRO");
        for (String tech : cleanTechs) {
            DecarbonizationScenarioRequest req = DecarbonizationScenarioRequest.builder()
                    .countryCode("USA")
                    .displacedFuel("FOSSIL_GAS")
                    .replacementCleanSource(tech)
                    .generationAmount(25.0)
                    .generationUnit("TWH")
                    .accountingBoundary("LIFECYCLE_SCOPE_1_2_3")
                    .build();

            DecarbonizationScenarioResponse res = service.calculateScenario(req);
            SensitivityIntervalDto interval = res.getImpact().getSensitivityInterval();

            assertNotNull(interval, "Interval must exist for " + tech);
            assertTrue(interval.getMinAvoidedEmissionsMt() <= interval.getMedianAvoidedEmissionsMt(),
                    "Min <= Median failed for " + tech);
            assertTrue(interval.getMedianAvoidedEmissionsMt() <= interval.getMaxAvoidedEmissionsMt(),
                    "Median <= Max failed for " + tech);
        }
    }

    @Test
    @DisplayName("Sensitivity Interval: Oil LCA factor is explicitly flagged as PROVISIONAL / UNVERIFIED in Table A.III.2")
    void testSensitivityInterval_OilLca_ExplicitlyFlaggedProvisional() {
        DecarbonizationScenarioRequest req = DecarbonizationScenarioRequest.builder()
                .countryCode("USA")
                .displacedFuel("OIL")
                .replacementCleanSource("SOLAR_PV")
                .generationAmount(5.0)
                .generationUnit("TWH")
                .accountingBoundary("LIFECYCLE_SCOPE_1_2_3")
                .build();

        DecarbonizationScenarioResponse res = service.calculateScenario(req);
        SensitivityIntervalDto interval = res.getImpact().getSensitivityInterval();

        assertNotNull(interval);
        assertTrue(interval.getUncertaintyDescription().contains("PROVISIONAL / UNVERIFIED IN TABLE A.III.2"),
                "Oil LCA uncertainty description must explicitly flag that it is not published in Table A.III.2");
        assertTrue(interval.getSourceCitation().contains("Provisional Peaker Estimate"),
                "Citation must declare provisional peaker estimate");
        assertTrue(interval.getMinAvoidedEmissionsMt() <= interval.getMedianAvoidedEmissionsMt());
        assertTrue(interval.getMedianAvoidedEmissionsMt() <= interval.getMaxAvoidedEmissionsMt());
    }

    @Test
    @DisplayName("Milestone 3.2: Multi-intervention composite scenario combines Solar and Wind without double counting")
    void testMultiIntervention_CompositeSolarAndWindDisplacingCoal_NoDoubleCounting() {
        ScenarioInterventionDto solar = ScenarioInterventionDto.builder()
                .id("solar-1")
                .cleanSource("SOLAR_PV")
                .targetDisplacedFuel("COAL")
                .generationAmount(40.0)
                .generationUnit("TWH")
                .build();

        ScenarioInterventionDto wind = ScenarioInterventionDto.builder()
                .id("wind-1")
                .cleanSource("WIND_ONSHORE")
                .targetDisplacedFuel("COAL")
                .generationAmount(30.0)
                .generationUnit("TWH")
                .build();

        DecarbonizationScenarioRequest req = DecarbonizationScenarioRequest.builder()
                .countryCode("IND")
                .accountingBoundary("LIFECYCLE_SCOPE_1_2_3")
                .interventions(List.of(solar, wind))
                .build();

        DecarbonizationScenarioResponse res = service.calculateScenario(req);
        assertNotNull(res);

        // Requested vs displaced balances
        assertEquals(70.0, res.getScenario().getTotalRequestedCleanGenerationTwh(), 0.001);
        assertEquals(70.0, res.getScenario().getTotalDisplacedFossilGenerationTwh(), 0.001);
        assertEquals(0.0, res.getScenario().getTotalUnusedCleanGenerationTwh(), 0.001);

        // Fossil reductions are sequentially accumulated
        // Coal baseline = 1445.44 -> simulated coal = 1445.44 - 70.0 = 1375.44
        assertEquals(1375.44, res.getScenario().getSimulatedCoalGenerationTwh(), 0.001);
        assertEquals(1375.44, res.getScenario().getSimulatedTargetFuelGenerationTwh(), 0.001);
        assertEquals(1430.09, res.getScenario().getSimulatedFossilGenerationTwh(), 0.001);
        assertEquals(488.17, res.getScenario().getSimulatedCleanGenerationTwh(), 0.001);
        assertEquals(1918.26, res.getScenario().getTotalGenerationTwh(), 0.001);

        // Itemized results
        List<InterventionResultDto> itemized = res.getImpact().getItemizedInterventions();
        assertEquals(2, itemized.size());

        // Item 1: 40 TWh Solar
        InterventionResultDto resSolar = itemized.get(0);
        assertEquals(40.0, resSolar.getRequestedCleanGenerationTwh(), 0.001);
        assertEquals(40.0, resSolar.getDisplacedFossilGenerationTwh(), 0.001);
        assertEquals(0.0, resSolar.getUnusedCleanGenerationTwh(), 0.001);
        assertFalse(resSolar.isCapped());
        // Gross: 40,000 * 820 / 10^6 = 32.80 Mt
        assertEquals(32.80, resSolar.getGrossAvoidedMt(), 0.001);
        // Clean LCA: 40,000 * 48 / 10^6 = 1.92 Mt
        assertEquals(1.92, resSolar.getCleanReplacementLifecycleMt(), 0.001);
        // Net: 32.80 - 1.92 = 30.88 Mt
        assertEquals(30.88, resSolar.getNetAvoidedMt(), 0.001);

        // Item 2: 30 TWh Wind
        InterventionResultDto resWind = itemized.get(1);
        assertEquals(30.0, resWind.getRequestedCleanGenerationTwh(), 0.001);
        assertEquals(30.0, resWind.getDisplacedFossilGenerationTwh(), 0.001);
        assertEquals(0.0, resWind.getUnusedCleanGenerationTwh(), 0.001);
        assertFalse(resWind.isCapped());
        // Gross: 30,000 * 820 / 10^6 = 24.60 Mt
        assertEquals(24.60, resWind.getGrossAvoidedMt(), 0.001);
        // Clean LCA: 30,000 * 11 / 10^6 = 0.33 Mt
        assertEquals(0.33, resWind.getCleanReplacementLifecycleMt(), 0.001);
        // Net: 24.60 - 0.33 = 24.27 Mt
        assertEquals(24.27, resWind.getNetAvoidedMt(), 0.001);

        // Composite net abatement = 30.88 + 24.27 = 55.15 Mt
        assertEquals(55.15, res.getImpact().getNetAvoidedEmissionsMt(), 0.001);
        assertEquals(57.40, res.getImpact().getGrossAvoidedEmissionsMt(), 0.001);
        assertEquals(2.25, res.getImpact().getCleanReplacementLifecycleEmissionsMt(), 0.001);

        // Monotonic sensitivity ordering for composite
        SensitivityIntervalDto interval = res.getImpact().getSensitivityInterval();
        assertTrue(interval.getMinAvoidedEmissionsMt() <= interval.getMedianAvoidedEmissionsMt());
        assertTrue(interval.getMedianAvoidedEmissionsMt() <= interval.getMaxAvoidedEmissionsMt());
    }

    @Test
    @DisplayName("Milestone 3.2: Exhausted fossil generation caps subsequent interventions and reports unused clean generation")
    void testMultiIntervention_ExhaustedFossilGeneration_CapsInterventionsAndReportsUnusedClean() {
        // GBR has 3.78 TWh baseline Coal generation
        ScenarioInterventionDto solar = ScenarioInterventionDto.builder()
                .id("solar-1")
                .cleanSource("SOLAR_PV")
                .targetDisplacedFuel("COAL")
                .generationAmount(2.0)
                .generationUnit("TWH")
                .build();

        ScenarioInterventionDto wind = ScenarioInterventionDto.builder()
                .id("wind-1")
                .cleanSource("WIND_OFFSHORE")
                .targetDisplacedFuel("COAL")
                .generationAmount(3.0)
                .generationUnit("TWH")
                .build();

        DecarbonizationScenarioRequest req = DecarbonizationScenarioRequest.builder()
                .countryCode("GBR")
                .accountingBoundary("OPERATIONAL_SCOPE_1")
                .interventions(List.of(solar, wind))
                .build();

        DecarbonizationScenarioResponse res = service.calculateScenario(req);
        assertNotNull(res);

        // Solar gets full 2.0 TWh, leaving 1.78 TWh coal
        // Wind requests 3.0 TWh but is capped at 1.78 TWh, leaving 1.22 TWh unused clean
        assertEquals(5.0, res.getScenario().getTotalRequestedCleanGenerationTwh(), 0.001);
        assertEquals(3.78, res.getScenario().getTotalDisplacedFossilGenerationTwh(), 0.001);
        assertEquals(1.22, res.getScenario().getTotalUnusedCleanGenerationTwh(), 0.001);

        // Coal generation non-negative invariant: exactly 0.0 TWh
        assertEquals(0.00, res.getScenario().getSimulatedCoalGenerationTwh(), 0.001);

        List<InterventionResultDto> itemized = res.getImpact().getItemizedInterventions();
        assertEquals(2, itemized.size());
        assertFalse(itemized.get(0).isCapped());
        assertTrue(itemized.get(1).isCapped());
        assertEquals(1.22, itemized.get(1).getUnusedCleanGenerationTwh(), 0.001);
        assertTrue(itemized.get(1).getConstraintNote().contains("exhausted"));

        assertTrue(res.getScenario().isConstraintsApplied());
        assertTrue(res.getImpact().isConstraintsApplied());
    }

    @Test
    @DisplayName("Milestone 3.2: Minimum residual baseload constraint caps clean substitution")
    void testMultiIntervention_MinResidualBaseloadConstraint_Enforced() {
        // IND has 1445.44 TWh coal. Set minimum residual coal floor at 1400.0 TWh
        ScenarioInterventionDto solar = ScenarioInterventionDto.builder()
                .id("solar-1")
                .cleanSource("SOLAR_PV")
                .targetDisplacedFuel("COAL")
                .generationAmount(60.0)
                .generationUnit("TWH")
                .build();

        ScenarioConstraintsDto constraints = ScenarioConstraintsDto.builder()
                .minResidualCoalTwh(1400.0)
                .build();

        DecarbonizationScenarioRequest req = DecarbonizationScenarioRequest.builder()
                .countryCode("IND")
                .accountingBoundary("OPERATIONAL_SCOPE_1")
                .interventions(List.of(solar))
                .constraints(constraints)
                .build();

        DecarbonizationScenarioResponse res = service.calculateScenario(req);
        assertNotNull(res);

        // Reducible coal = 1445.44 - 1400.0 = 45.44 TWh
        // Requested = 60.0 TWh -> Displaced = 45.44 TWh, Unused = 14.56 TWh
        assertEquals(60.0, res.getScenario().getTotalRequestedCleanGenerationTwh(), 0.001);
        assertEquals(45.44, res.getScenario().getTotalDisplacedFossilGenerationTwh(), 0.001);
        assertEquals(14.56, res.getScenario().getTotalUnusedCleanGenerationTwh(), 0.001);

        // Floor respected
        assertEquals(1400.0, res.getScenario().getSimulatedCoalGenerationTwh(), 0.001);
        assertTrue(res.getScenario().isConstraintsApplied());

        InterventionResultDto resultSolar = res.getImpact().getItemizedInterventions().get(0);
        assertTrue(resultSolar.isCapped());
        assertTrue(resultSolar.getConstraintNote().contains("minimum residual fossil"));
    }

    @Test
    @DisplayName("Milestone 3.2: Maximum clean addition limit caps dispatch across multiple interventions")
    void testMultiIntervention_MaxCleanAdditionLimit_Enforced() {
        // USA: Gas = 1806.06 TWh. Request 50 TWh Solar with max clean cap of 35 TWh
        ScenarioInterventionDto solar = ScenarioInterventionDto.builder()
                .id("solar-1")
                .cleanSource("SOLAR_PV")
                .targetDisplacedFuel("FOSSIL_GAS")
                .generationAmount(50.0)
                .generationUnit("TWH")
                .build();

        ScenarioConstraintsDto constraints = ScenarioConstraintsDto.builder()
                .maxCleanAdditionLimitTwh(35.0)
                .build();

        DecarbonizationScenarioRequest req = DecarbonizationScenarioRequest.builder()
                .countryCode("USA")
                .accountingBoundary("OPERATIONAL_SCOPE_1")
                .interventions(List.of(solar))
                .constraints(constraints)
                .build();

        DecarbonizationScenarioResponse res = service.calculateScenario(req);
        assertNotNull(res);

        assertEquals(50.0, res.getScenario().getTotalRequestedCleanGenerationTwh(), 0.001);
        assertEquals(35.0, res.getScenario().getTotalDisplacedFossilGenerationTwh(), 0.001);
        assertEquals(15.0, res.getScenario().getTotalUnusedCleanGenerationTwh(), 0.001);

        InterventionResultDto resultSolar = res.getImpact().getItemizedInterventions().get(0);
        assertTrue(resultSolar.isCapped());
        assertTrue(resultSolar.getConstraintNote().contains("maximum clean addition limit"));
    }

    @Test
    @DisplayName("Milestone 3.2: Multi-intervention composite sensitivity bounds maintain strict mathematical ordering")
    void testMultiIntervention_CompositeSensitivityBounds_MonotonicOrdering() {
        ScenarioInterventionDto solar = ScenarioInterventionDto.builder()
                .id("solar-1")
                .cleanSource("SOLAR_PV")
                .targetDisplacedFuel("FOSSIL_GAS")
                .generationAmount(20.0)
                .generationUnit("TWH")
                .build();

        ScenarioInterventionDto wind = ScenarioInterventionDto.builder()
                .id("wind-1")
                .cleanSource("WIND_ONSHORE")
                .targetDisplacedFuel("COAL")
                .generationAmount(30.0)
                .generationUnit("TWH")
                .build();

        ScenarioInterventionDto hydro = ScenarioInterventionDto.builder()
                .id("hydro-1")
                .cleanSource("HYDRO")
                .targetDisplacedFuel("OIL")
                .generationAmount(10.0)
                .generationUnit("TWH")
                .build();

        DecarbonizationScenarioRequest req = DecarbonizationScenarioRequest.builder()
                .countryCode("USA")
                .accountingBoundary("LIFECYCLE_SCOPE_1_2_3")
                .interventions(List.of(solar, wind, hydro))
                .build();

        DecarbonizationScenarioResponse res = service.calculateScenario(req);
        SensitivityIntervalDto interval = res.getImpact().getSensitivityInterval();

        assertNotNull(interval);
        assertTrue(interval.getMinAvoidedEmissionsMt() <= interval.getMedianAvoidedEmissionsMt(),
                "Composite min must be <= median");
        assertTrue(interval.getMedianAvoidedEmissionsMt() <= interval.getMaxAvoidedEmissionsMt(),
                "Composite median must be <= max");
        assertTrue(interval.getUncertaintyDescription().contains("Composite uncertainty"));
    }

    @Test
    @DisplayName("Milestone 3.2: Invalid constraint values are rejected by validator")
    void testMultiIntervention_InvalidConstraintValidation() {
        DecarbonizationScenarioRequest negConstraint = DecarbonizationScenarioRequest.builder()
                .countryCode("IND")
                .displacedFuel("COAL")
                .replacementCleanSource("SOLAR_PV")
                .generationAmount(10.0)
                .generationUnit("TWH")
                .constraints(ScenarioConstraintsDto.builder().minResidualCoalTwh(-5.0).build())
                .build();

        SimulatorValidationException ex1 = assertThrows(SimulatorValidationException.class, () ->
                service.calculateScenario(negConstraint));
        assertTrue(ex1.getMessage().contains("cannot be negative"));

        DecarbonizationScenarioRequest excessiveConstraint = DecarbonizationScenarioRequest.builder()
                .countryCode("IND")
                .displacedFuel("COAL")
                .replacementCleanSource("SOLAR_PV")
                .generationAmount(10.0)
                .generationUnit("TWH")
                .constraints(ScenarioConstraintsDto.builder().minResidualCoalTwh(2000.0).build())
                .build();

        SimulatorValidationException ex2 = assertThrows(SimulatorValidationException.class, () ->
                service.calculateScenario(excessiveConstraint));
        assertTrue(ex2.getMessage().contains("exceeds baseline Coal generation"));
    }

    @Test
    @DisplayName("Audit 1: Non-finite generation amounts and custom emission factors are rejected")
    void testAudit_NonFiniteAndZeroInputs_Rejected() {
        // Zero generation
        DecarbonizationScenarioRequest zeroReq = DecarbonizationScenarioRequest.builder()
                .countryCode("IND")
                .displacedFuel("COAL")
                .replacementCleanSource("SOLAR_PV")
                .generationAmount(0.0)
                .generationUnit("TWH")
                .build();
        assertThrows(SimulatorValidationException.class, () -> service.calculateScenario(zeroReq));

        // NaN generation
        DecarbonizationScenarioRequest nanReq = DecarbonizationScenarioRequest.builder()
                .countryCode("IND")
                .displacedFuel("COAL")
                .replacementCleanSource("SOLAR_PV")
                .generationAmount(Double.NaN)
                .generationUnit("TWH")
                .build();
        assertThrows(SimulatorValidationException.class, () -> service.calculateScenario(nanReq));

        // Infinity generation
        DecarbonizationScenarioRequest infReq = DecarbonizationScenarioRequest.builder()
                .countryCode("IND")
                .displacedFuel("COAL")
                .replacementCleanSource("SOLAR_PV")
                .generationAmount(Double.POSITIVE_INFINITY)
                .generationUnit("TWH")
                .build();
        assertThrows(SimulatorValidationException.class, () -> service.calculateScenario(infReq));

        // Multi-intervention non-finite
        ScenarioInterventionDto nanItem = ScenarioInterventionDto.builder()
                .cleanSource("SOLAR_PV")
                .targetDisplacedFuel("COAL")
                .generationAmount(Double.NaN)
                .generationUnit("TWH")
                .build();
        DecarbonizationScenarioRequest multiNan = DecarbonizationScenarioRequest.builder()
                .countryCode("IND")
                .interventions(List.of(nanItem))
                .build();
        assertThrows(SimulatorValidationException.class, () -> service.calculateScenario(multiNan));

        // Non-finite custom factor
        ScenarioInterventionDto nanFactor = ScenarioInterventionDto.builder()
                .cleanSource("SOLAR_PV")
                .targetDisplacedFuel("COAL")
                .generationAmount(10.0)
                .generationUnit("TWH")
                .customDisplacedEmissionFactor(Double.NaN)
                .build();
        DecarbonizationScenarioRequest factorReq = DecarbonizationScenarioRequest.builder()
                .countryCode("IND")
                .interventions(List.of(nanFactor))
                .build();
        assertThrows(SimulatorValidationException.class, () -> service.calculateScenario(factorReq));
    }

    @Test
    @DisplayName("Audit 2: Contradictory dispatch constraints (total fossil floor < sum of fuel floors) are rejected")
    void testAudit_ContradictoryConstraints_Rejected() {
        ScenarioConstraintsDto contradictory = ScenarioConstraintsDto.builder()
                .minResidualCoalTwh(500.0)
                .minResidualGasTwh(40.0)
                .minResidualOilTwh(2.0)
                .minResidualFossilTwh(500.0) // 500 < 542
                .build();

        DecarbonizationScenarioRequest req = DecarbonizationScenarioRequest.builder()
                .countryCode("IND")
                .displacedFuel("COAL")
                .replacementCleanSource("SOLAR_PV")
                .generationAmount(10.0)
                .generationUnit("TWH")
                .constraints(contradictory)
                .build();

        SimulatorValidationException ex = assertThrows(SimulatorValidationException.class, () ->
                service.calculateScenario(req));
        assertTrue(ex.getMessage().contains("cannot be less than the sum of individual fuel floors"));
    }

    @Test
    @DisplayName("Audit 3: Simultaneous per-fuel baseload floors and total fossil floor are both respected without violation")
    void testAudit_SimultaneousPerFuelAndTotalFossilFloors_Enforced() {
        // India baseline: Coal=1445.44, Gas=51.27, Oil=3.38. Total Fossil=1500.09.
        // minCoal = 1400.0 (reducible coal = 45.44)
        // minGas = 40.0 (reducible gas = 11.27)
        // minOil = 2.0 (reducible oil = 1.38)
        // sum fuel floors = 1442.0.
        // minFossil = 1460.0 (higher than 1442.0! reducible total fossil = 1500.09 - 1460.0 = 40.09 TWh).
        ScenarioConstraintsDto constraints = ScenarioConstraintsDto.builder()
                .minResidualCoalTwh(1400.0)
                .minResidualGasTwh(40.0)
                .minResidualOilTwh(2.0)
                .minResidualFossilTwh(1460.0)
                .build();

        ScenarioInterventionDto solar = ScenarioInterventionDto.builder()
                .id("sol-1")
                .cleanSource("SOLAR_PV")
                .targetDisplacedFuel("COAL")
                .generationAmount(30.0)
                .generationUnit("TWH")
                .build();

        ScenarioInterventionDto wind = ScenarioInterventionDto.builder()
                .id("wnd-1")
                .cleanSource("WIND_ONSHORE")
                .targetDisplacedFuel("COAL")
                .generationAmount(30.0)
                .generationUnit("TWH")
                .build();

        DecarbonizationScenarioRequest req = DecarbonizationScenarioRequest.builder()
                .countryCode("IND")
                .interventions(List.of(solar, wind))
                .constraints(constraints)
                .build();

        DecarbonizationScenarioResponse res = service.calculateScenario(req);
        DecarbonizationResultsDto scenario = res.getScenario();

        // Total requested = 60 TWh. Reducible total fossil = 40.09 TWh.
        assertEquals(40.09, scenario.getTotalDisplacedFossilGenerationTwh(), 0.01);
        assertEquals(19.91, scenario.getTotalUnusedCleanGenerationTwh(), 0.01);

        // Verification of floor invariants
        assertTrue(scenario.getSimulatedCoalGenerationTwh() >= 1400.0, "Coal floor (1400) must be preserved");
        assertTrue(scenario.getSimulatedGasGenerationTwh() >= 40.0, "Gas floor (40) must be preserved");
        assertTrue(scenario.getSimulatedOilGenerationTwh() >= 2.0, "Oil floor (2) must be preserved");
        assertEquals(1460.00, scenario.getSimulatedFossilGenerationTwh(), 0.01, "Total fossil floor (1460) must be exactly preserved");
    }

    @Test
    @DisplayName("Audit 4: No reducible fossil available produces zero displacement, zero emissions, and full surplus")
    void testAudit_NoReducibleFossil_ZeroEmissionsAndFullSurplus() {
        // India Coal baseline = 1445.44. Set floor to full baseline:
        ScenarioConstraintsDto fullFloor = ScenarioConstraintsDto.builder()
                .minResidualCoalTwh(1445.44)
                .build();

        DecarbonizationScenarioRequest req = DecarbonizationScenarioRequest.builder()
                .countryCode("IND")
                .accountingBoundary("LIFECYCLE_SCOPE_1_2_3")
                .displacedFuel("COAL")
                .replacementCleanSource("SOLAR_PV")
                .generationAmount(50.0)
                .generationUnit("TWH")
                .constraints(fullFloor)
                .build();

        DecarbonizationScenarioResponse res = service.calculateScenario(req);
        assertEquals(0.0, res.getScenario().getTotalDisplacedFossilGenerationTwh(), 0.001);
        assertEquals(50.0, res.getScenario().getTotalUnusedCleanGenerationTwh(), 0.001);

        DecarbonizationImpactDto impact = res.getImpact();
        assertEquals(0.0, impact.getGrossAvoidedEmissionsMt(), 0.001);
        assertEquals(0.0, impact.getCleanReplacementLifecycleEmissionsMt(), 0.001);
        assertEquals(0.0, impact.getNetAvoidedEmissionsMt(), 0.001);

        SensitivityIntervalDto interval = impact.getSensitivityInterval();
        assertEquals(0.0, interval.getMinAvoidedEmissionsMt(), 0.001);
        assertEquals(0.0, interval.getMedianAvoidedEmissionsMt(), 0.001);
        assertEquals(0.0, interval.getMaxAvoidedEmissionsMt(), 0.001);
    }

    @Test
    @DisplayName("Audit 5: Clean addition cap restricts clean displacement; replacement emissions use displaced generation")
    void testAudit_CleanAdditionCap_CleanEmbodiedUsesDisplacedGeneration() {
        ScenarioConstraintsDto capConstraint = ScenarioConstraintsDto.builder()
                .maxCleanAdditionLimitTwh(25.0)
                .build();

        DecarbonizationScenarioRequest req = DecarbonizationScenarioRequest.builder()
                .countryCode("IND")
                .accountingBoundary("LIFECYCLE_SCOPE_1_2_3")
                .displacedFuel("COAL")
                .replacementCleanSource("SOLAR_PV")
                .generationAmount(100.0)
                .generationUnit("TWH")
                .constraints(capConstraint)
                .build();

        DecarbonizationScenarioResponse res = service.calculateScenario(req);
        assertEquals(25.0, res.getScenario().getTotalDisplacedFossilGenerationTwh(), 0.001);
        assertEquals(75.0, res.getScenario().getTotalUnusedCleanGenerationTwh(), 0.001);

        DecarbonizationImpactDto impact = res.getImpact();
        // Displaced generation = 25 TWh = 25,000 GWh
        // Gross LCA = 25,000 * 820 / 10^6 = 20.50 Mt
        // Clean replacement LCA = 25,000 * 48 / 10^6 = 1.20 Mt (must be based on 25 TWh, NOT 100 TWh!)
        assertEquals(20.50, impact.getGrossDisplacedLifecycleMt(), 0.001);
        assertEquals(1.20, impact.getCleanEmbodiedLifecycleMt(), 0.001);
        assertEquals(19.30, impact.getNetAvoidedLifecycleMt(), 0.001);

        // Assumptions DTO declares replacement generation as 25 TWh
        assertEquals(25.0, res.getAssumptions().getReplacementGenerationTwh(), 0.001);

        // Sensitivity interval monotonic ordering
        SensitivityIntervalDto interval = impact.getSensitivityInterval();
        assertEquals(14.00, interval.getMinAvoidedEmissionsMt(), 0.01); // 25,000 * (740 - 180) / 10^6 = 14.00
        assertEquals(19.30, interval.getMedianAvoidedEmissionsMt(), 0.01); // 25,000 * (820 - 48) / 10^6 = 19.30
        assertEquals(22.30, interval.getMaxAvoidedEmissionsMt(), 0.01); // 25,000 * (910 - 18) / 10^6 = 22.30
        assertTrue(interval.getMinAvoidedEmissionsMt() <= interval.getMedianAvoidedEmissionsMt());
        assertTrue(interval.getMedianAvoidedEmissionsMt() <= interval.getMaxAvoidedEmissionsMt());
    }

    @Test
    @DisplayName("Audit 6: Single-intervention Gas or Oil correctly populates targetFuelGenerationTwh in baseline and scenario")
    void testAudit_TargetFuelGeneration_MatchesTargetFuelForGasAndOil() {
        // UK Baseline: Coal=3.78, Gas=101.66, Oil=10.82.
        DecarbonizationScenarioRequest gasReq = DecarbonizationScenarioRequest.builder()
                .countryCode("GBR")
                .displacedFuel("FOSSIL_GAS")
                .replacementCleanSource("WIND_OFFSHORE")
                .generationAmount(20.0)
                .generationUnit("TWH")
                .build();

        DecarbonizationScenarioResponse gasRes = service.calculateScenario(gasReq);
        assertEquals(101.66, gasRes.getBaseline().getTargetFuelGenerationTwh(), 0.01, "Gas baseline must be 101.66");
        assertEquals(81.66, gasRes.getScenario().getSimulatedTargetFuelGenerationTwh(), 0.01, "Gas simulated must be 81.66");

        DecarbonizationScenarioRequest oilReq = DecarbonizationScenarioRequest.builder()
                .countryCode("GBR")
                .displacedFuel("OIL")
                .replacementCleanSource("SOLAR_PV")
                .generationAmount(1.0)
                .generationUnit("TWH")
                .build();

        DecarbonizationScenarioResponse oilRes = service.calculateScenario(oilReq);
        assertEquals(10.82, oilRes.getBaseline().getTargetFuelGenerationTwh(), 0.01, "Oil baseline must be 10.82");
        assertEquals(9.82, oilRes.getScenario().getSimulatedTargetFuelGenerationTwh(), 0.01, "Oil simulated must be 9.82");
    }

    @Test
    @DisplayName("Audit 7: Composite sensitivity interval reconciles exactly with sum of itemized results")
    void testAudit_CompositeIntervalReconcilesExactlyWithItemizedSum() {
        ScenarioInterventionDto int1 = ScenarioInterventionDto.builder()
                .id("int-1")
                .cleanSource("SOLAR_PV")
                .targetDisplacedFuel("COAL")
                .generationAmount(20.0)
                .generationUnit("TWH")
                .build();

        ScenarioInterventionDto int2 = ScenarioInterventionDto.builder()
                .id("int-2")
                .cleanSource("WIND_ONSHORE")
                .targetDisplacedFuel("FOSSIL_GAS")
                .generationAmount(30.0)
                .generationUnit("TWH")
                .build();

        ScenarioInterventionDto int3 = ScenarioInterventionDto.builder()
                .id("int-3")
                .cleanSource("HYDRO")
                .targetDisplacedFuel("OIL")
                .generationAmount(2.0)
                .generationUnit("TWH")
                .build();

        DecarbonizationScenarioRequest req = DecarbonizationScenarioRequest.builder()
                .countryCode("USA")
                .accountingBoundary("LIFECYCLE_SCOPE_1_2_3")
                .interventions(List.of(int1, int2, int3))
                .build();

        DecarbonizationScenarioResponse res = service.calculateScenario(req);
        List<InterventionResultDto> items = res.getImpact().getItemizedInterventions();
        assertEquals(3, items.size());

        double sumItemMin = items.stream().mapToDouble(InterventionResultDto::getSensitivityMinMt).sum();
        double sumItemMed = items.stream().mapToDouble(InterventionResultDto::getSensitivityMedianMt).sum();
        double sumItemMax = items.stream().mapToDouble(InterventionResultDto::getSensitivityMaxMt).sum();

        SensitivityIntervalDto interval = res.getImpact().getSensitivityInterval();
        assertEquals(sumItemMin, interval.getMinAvoidedEmissionsMt(), 1e-6, "Composite min must equal sum of itemized mins");
        assertEquals(sumItemMed, interval.getMedianAvoidedEmissionsMt(), 1e-6, "Composite median must equal sum of itemized medians");
        assertEquals(sumItemMax, interval.getMaxAvoidedEmissionsMt(), 1e-6, "Composite max must equal sum of itemized maxes");
        assertTrue(interval.getMinAvoidedEmissionsMt() <= interval.getMedianAvoidedEmissionsMt());
        assertTrue(interval.getMedianAvoidedEmissionsMt() <= interval.getMaxAvoidedEmissionsMt());
    }

    @Test
    @DisplayName("Audit 8: A fuel unit displaced by one intervention cannot be displaced again; non-negative fossil enforced")
    void testAudit_FuelDisplacedByOneCannotBeDisplacedAgain() {
        // UK Coal baseline = 3.78 TWh.
        ScenarioInterventionDto int1 = ScenarioInterventionDto.builder()
                .id("c1")
                .cleanSource("SOLAR_PV")
                .targetDisplacedFuel("COAL")
                .generationAmount(3.0)
                .generationUnit("TWH")
                .build();

        ScenarioInterventionDto int2 = ScenarioInterventionDto.builder()
                .id("c2")
                .cleanSource("WIND_ONSHORE")
                .targetDisplacedFuel("COAL")
                .generationAmount(3.0)
                .generationUnit("TWH")
                .build();

        DecarbonizationScenarioRequest req = DecarbonizationScenarioRequest.builder()
                .countryCode("GBR")
                .interventions(List.of(int1, int2))
                .build();

        DecarbonizationScenarioResponse res = service.calculateScenario(req);
        List<InterventionResultDto> items = res.getImpact().getItemizedInterventions();

        // First intervention takes 3.0 TWh
        assertEquals(3.0, items.get(0).getDisplacedFossilGenerationTwh(), 0.001);
        assertEquals(0.0, items.get(0).getUnusedCleanGenerationTwh(), 0.001);

        // Second intervention can only take the remaining 0.78 TWh; 2.22 TWh is unused
        assertEquals(0.78, items.get(1).getDisplacedFossilGenerationTwh(), 0.001);
        assertEquals(2.22, items.get(1).getUnusedCleanGenerationTwh(), 0.001);

        // Total displaced = exactly 3.78 TWh. Remaining coal = 0.00 TWh.
        assertEquals(3.78, res.getScenario().getTotalDisplacedFossilGenerationTwh(), 0.001);
        assertEquals(0.00, res.getScenario().getSimulatedCoalGenerationTwh(), 0.001);
        assertTrue(res.getScenario().getSimulatedCoalGenerationTwh() >= 0.0, "Coal generation must never be negative");
    }
}

