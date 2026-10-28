package com.carbonscope.service;

import com.carbonscope.dto.policy.*;
import com.carbonscope.dto.simulator.DecarbonizationScenarioRequest;
import com.carbonscope.dto.simulator.DecarbonizationScenarioResponse;
import com.carbonscope.exception.SimulatorValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PolicyAnalysisServiceTest {

    private DecarbonizationSimulatorService simulatorService;
    private PolicyAnalysisService policyService;

    @BeforeEach
    void setUp() {
        ReliableCarbonSourcesService reliableSources = new ReliableCarbonSourcesService();
        simulatorService = new DecarbonizationSimulatorService(reliableSources);
        policyService = new PolicyAnalysisService(simulatorService);
    }

    @Test
    @DisplayName("1. Sourced lifecycle factors match IPCC AR5 WGIII Table A.III.2 exactly across all 8 technologies")
    void testGetAllInterventions_TableAIII2VerifiedValues() {
        List<InterventionEvidenceDto> list = policyService.getAllInterventions();
        assertEquals(8, list.size(), "Must contain 8 evidence-backed technologies");

        // Utility-scale Solar PV (Must match utility-scale ground mount 18 / 48 / 180, not rooftop 26 / 41 / 60)
        InterventionEvidenceDto solar = policyService.getInterventionById("SOLAR_PV");
        assertEquals("Utility-Scale Solar PV (Crystalline Silicon)", solar.getDisplayName());
        assertEquals("CLEAN_GENERATION", solar.getCategory());
        assertEquals(0.0, solar.getDirectFactorGco2PerKwh());
        assertEquals(48.0, solar.getLifecycleFactorMedianGco2PerKwh(), "Solar PV utility median must be 48");
        assertEquals(18.0, solar.getLifecycleFactorMinGco2PerKwh(), "Solar PV utility min must be 18 (Table A.III.2)");
        assertEquals(180.0, solar.getLifecycleFactorMaxGco2PerKwh(), "Solar PV utility max must be 180 (Table A.III.2)");
        assertFalse(solar.isProvisional());
        assertFalse(solar.isLcoeDataAvailable(), "LCOE must be marked unavailable to prevent fabrication");
        assertNotNull(solar.getLcoeUnavailableReason());
        assertFalse(solar.isCapacityFactorDataAvailable(), "Capacity factor must be marked unavailable");

        // Onshore Wind: 7 / 11 / 56
        InterventionEvidenceDto windOnshore = policyService.getInterventionById("WIND_ONSHORE");
        assertEquals(11.0, windOnshore.getLifecycleFactorMedianGco2PerKwh());
        assertEquals(7.0, windOnshore.getLifecycleFactorMinGco2PerKwh());
        assertEquals(56.0, windOnshore.getLifecycleFactorMaxGco2PerKwh());

        // Offshore Wind: 8 / 12 / 35
        InterventionEvidenceDto windOffshore = policyService.getInterventionById("WIND_OFFSHORE");
        assertEquals(12.0, windOffshore.getLifecycleFactorMedianGco2PerKwh());
        assertEquals(8.0, windOffshore.getLifecycleFactorMinGco2PerKwh());
        assertEquals(35.0, windOffshore.getLifecycleFactorMaxGco2PerKwh());

        // Nuclear: 3.7 / 12 / 110
        InterventionEvidenceDto nuclear = policyService.getInterventionById("NUCLEAR");
        assertEquals(12.0, nuclear.getLifecycleFactorMedianGco2PerKwh());
        assertEquals(3.7, nuclear.getLifecycleFactorMinGco2PerKwh());
        assertEquals(110.0, nuclear.getLifecycleFactorMaxGco2PerKwh());

        // Hydro: 1.0 / 24 / 2200
        InterventionEvidenceDto hydro = policyService.getInterventionById("HYDRO");
        assertEquals(24.0, hydro.getLifecycleFactorMedianGco2PerKwh());
        assertEquals(1.0, hydro.getLifecycleFactorMinGco2PerKwh());
        assertEquals(2200.0, hydro.getLifecycleFactorMaxGco2PerKwh());

        // Coal: 740 / 820 / 910 lifecycle; 950 direct
        InterventionEvidenceDto coal = policyService.getInterventionById("COAL");
        assertEquals("FOSSIL_DISPLACEMENT", coal.getCategory());
        assertEquals(950.0, coal.getDirectFactorGco2PerKwh());
        assertEquals(820.0, coal.getLifecycleFactorMedianGco2PerKwh());
        assertEquals(740.0, coal.getLifecycleFactorMinGco2PerKwh());
        assertEquals(910.0, coal.getLifecycleFactorMaxGco2PerKwh());

        // Gas: 410 / 490 / 650 lifecycle; 410 direct
        InterventionEvidenceDto gas = policyService.getInterventionById("FOSSIL_GAS");
        assertEquals("FOSSIL_DISPLACEMENT", gas.getCategory());
        assertEquals(410.0, gas.getDirectFactorGco2PerKwh());
        assertEquals(490.0, gas.getLifecycleFactorMedianGco2PerKwh());
        assertEquals(410.0, gas.getLifecycleFactorMinGco2PerKwh());
        assertEquals(650.0, gas.getLifecycleFactorMaxGco2PerKwh());

        // Oil: Explicitly provisional peaker factor (500 / 750 / 800)
        InterventionEvidenceDto oil = policyService.getInterventionById("OIL");
        assertTrue(oil.isProvisional(), "Oil lifecycle factor must remain explicitly flagged as provisional");
        assertEquals(720.0, oil.getDirectFactorGco2PerKwh());
        assertEquals(750.0, oil.getLifecycleFactorMedianGco2PerKwh());
        assertEquals(500.0, oil.getLifecycleFactorMinGco2PerKwh());
        assertEquals(800.0, oil.getLifecycleFactorMaxGco2PerKwh());
        assertNotNull(oil.getProvisionalWarning());
        assertTrue(oil.getProvisionalWarning().contains("PROVISIONAL / UNVERIFIED IN TABLE A.III.2"));
    }

    @Test
    @DisplayName("2. India NDC includes 2026 NDC 3.0 submission, historical 2030 targets, and domestic policy classification")
    void testNdcCurrentness_India2026Submission() {
        CountryNdcProfileDto ind = policyService.getNdcProfile("IND");
        assertEquals("India", ind.getCountryName());
        assertEquals("2026-04-24", ind.getLatestSubmissionDate(), "Must record latest April 2026 submission date");
        assertEquals("NDC 3.0 (2031–2035 Horizon)", ind.getActiveNdcCycle());
        assertEquals("2026-10-28", ind.getVerificationDate());

        // Must preserve both 2035 active targets and 2030 historical milestones
        List<NdcTargetDto> targets = ind.getTargets();
        assertTrue(targets.size() >= 6, "Must contain 2035 targets, 2030 historical targets, and domestic policy target");

        // Active 2035 GDP Intensity
        NdcTargetDto t2035Gdp = targets.stream().filter(t -> "IND-2035-T1".equals(t.getId())).findFirst().orElseThrow();
        assertEquals(47.0, t2035Gdp.getTargetValue());
        assertEquals(2035, t2035Gdp.getTargetYear());
        assertEquals("ACTIVE_LATEST_COMMITMENT", t2035Gdp.getTargetCurrencyStatus());
        assertEquals("OFFICIAL_NDC_COMMITMENT", t2035Gdp.getTargetClassification());

        // Active 2035 Capacity Share
        NdcTargetDto t2035Cap = targets.stream().filter(t -> "IND-2035-T2".equals(t.getId())).findFirst().orElseThrow();
        assertEquals(60.0, t2035Cap.getTargetValue());
        assertEquals(2035, t2035Cap.getTargetYear());
        assertEquals("RENEWABLE_SHARE_CAPACITY", t2035Cap.getTargetType());
        assertEquals("ACTIVE_LATEST_COMMITMENT", t2035Cap.getTargetCurrencyStatus());
        assertEquals("OFFICIAL_NDC_COMMITMENT", t2035Cap.getTargetClassification());

        // Historical 2030 Milestone
        NdcTargetDto t2030Cap = targets.stream().filter(t -> "IND-2030-T2".equals(t.getId())).findFirst().orElseThrow();
        assertEquals(50.0, t2030Cap.getTargetValue());
        assertEquals(2030, t2030Cap.getTargetYear());
        assertEquals("HISTORICAL_INTERMEDIATE_TARGET", t2030Cap.getTargetCurrencyStatus());

        // Domestic Statutory Target
        NdcTargetDto tDom = targets.stream().filter(t -> "IND-DOM-NEP".equals(t.getId())).findFirst().orElseThrow();
        assertEquals("DOMESTIC_POLICY_LAW", tDom.getTargetClassification());
        assertEquals("STATUTORY_DOMESTIC", tDom.getTargetCurrencyStatus());
        assertEquals(500.0, tDom.getTargetValue());
        assertEquals("GW", tDom.getTargetUnit());
    }

    @Test
    @DisplayName("3. EU Member States distinguish Joint EU NDC from separate domestic statutory laws")
    void testNdcCurrentness_EuJointFulfilmentAndDomesticDistinction() {
        CountryNdcProfileDto deu = policyService.getNdcProfile("DEU");
        assertEquals("JOINT_EU_FULFILMENT", deu.getPolicyReviewStatus());
        assertEquals("2025-03-15", deu.getLatestSubmissionDate());
        assertEquals(2035, deu.getTargetYearPrimary());

        // EU Joint 2030 target
        NdcTargetDto eu2030 = deu.getTargets().stream().filter(t -> "DEU-EU-2030".equals(t.getId())).findFirst().orElseThrow();
        assertEquals("JOINT_EU_NDC_COMMITMENT", eu2030.getTargetClassification());
        assertEquals("HISTORICAL_INTERMEDIATE_TARGET", eu2030.getTargetCurrencyStatus());
        assertEquals(55.0, eu2030.getTargetValue());

        // EU Joint 2035 target
        NdcTargetDto eu2035 = deu.getTargets().stream().filter(t -> "DEU-EU-2035".equals(t.getId())).findFirst().orElseThrow();
        assertEquals("JOINT_EU_NDC_COMMITMENT", eu2035.getTargetClassification());
        assertEquals("ACTIVE_LATEST_COMMITMENT", eu2035.getTargetCurrencyStatus());
        assertEquals("66.25% to 72.5%", eu2035.getTargetRange());

        // Domestic German law target (EEG 2023 80% RE electricity generation)
        NdcTargetDto deuPower = deu.getTargets().stream().filter(t -> "DEU-DOM-POWER".equals(t.getId())).findFirst().orElseThrow();
        assertEquals("DOMESTIC_POLICY_LAW", deuPower.getTargetClassification());
        assertEquals("STATUTORY_DOMESTIC", deuPower.getTargetCurrencyStatus());
        assertEquals(80.0, deuPower.getTargetValue());
        assertEquals("RENEWABLE_SHARE_GENERATION", deuPower.getTargetType());
        assertTrue(deuPower.getInterpretationCaveats().stream().anyMatch(c -> c.contains("Domestic policy target under national law")));
    }

    @Test
    @DisplayName("4. UK 2035 NDC submission accurately represents 81% target and preserves historical 68% milestone")
    void testNdcCurrentness_UnitedKingdom() {
        CountryNdcProfileDto gbr = policyService.getNdcProfile("GBR");
        assertEquals("2025-01-30", gbr.getLatestSubmissionDate());
        assertEquals("NDC 3.0 (2035 Horizon)", gbr.getActiveNdcCycle());

        NdcTargetDto gbr2035 = gbr.getTargets().stream().filter(t -> "GBR-2035-T1".equals(t.getId())).findFirst().orElseThrow();
        assertEquals(81.0, gbr2035.getTargetValue());
        assertEquals(2035, gbr2035.getTargetYear());
        assertEquals("ACTIVE_LATEST_COMMITMENT", gbr2035.getTargetCurrencyStatus());
        assertEquals("OFFICIAL_NDC_COMMITMENT", gbr2035.getTargetClassification());

        NdcTargetDto gbr2030 = gbr.getTargets().stream().filter(t -> "GBR-2030-T1".equals(t.getId())).findFirst().orElseThrow();
        assertEquals(68.0, gbr2030.getTargetValue());
        assertEquals(2030, gbr2030.getTargetYear());
        assertEquals("HISTORICAL_INTERMEDIATE_TARGET", gbr2030.getTargetCurrencyStatus());

        NdcTargetDto gbrDom = gbr.getTargets().stream().filter(t -> "GBR-DOM-CLEAN".equals(t.getId())).findFirst().orElseThrow();
        assertEquals("DOMESTIC_POLICY_LAW", gbrDom.getTargetClassification());
    }

    @Test
    @DisplayName("5. United States profile accurately represents Paris Agreement treaty withdrawal (effective Jan 27, 2026)")
    void testNdcCurrentness_UnitedStatesTreatyWithdrawalStatus() {
        CountryNdcProfileDto usa = policyService.getNdcProfile("USA");
        assertEquals("WITHDRAWN_PARIS_AGREEMENT", usa.getPolicyReviewStatus());
        assertEquals("WITHDRAWN_NON_PARTY", usa.getTreatyParticipationStatus());
        assertEquals("2026-01-27", usa.getTreatyWithdrawalEffectiveDate());
        assertEquals("2024-12-18", usa.getLatestSubmissionDate());
        assertEquals("Withdrawn (Effective January 27, 2026)", usa.getActiveNdcCycle());

        NdcTargetDto usa2035 = usa.getTargets().stream().filter(t -> "USA-2035-T1".equals(t.getId())).findFirst().orElseThrow();
        assertEquals("OFFICIAL_NDC_COMMITMENT", usa2035.getTargetClassification());
        assertEquals("HISTORICAL_SUPERSEDED_OR_WITHDRAWN", usa2035.getTargetCurrencyStatus());
        assertTrue(usa2035.getInterpretationCaveats().stream().anyMatch(c -> c.contains("TREATY WITHDRAWAL EFFECTIVE")));

        NdcTargetDto usa2030 = usa.getTargets().stream().filter(t -> "USA-2030-T1".equals(t.getId())).findFirst().orElseThrow();
        assertEquals("HISTORICAL_SUPERSEDED_OR_WITHDRAWN", usa2030.getTargetCurrencyStatus());

        NdcTargetDto usaCfe = usa.getTargets().stream().filter(t -> "USA-DOM-CFE".equals(t.getId())).findFirst().orElseThrow();
        assertEquals("DOMESTIC_POLICY_LAW", usaCfe.getTargetClassification());
    }

    @Test
    @DisplayName("5b. US scenario comparison against historical NDC explicitly suppresses treaty alignment and sets NON_PARTY_TREATY_WITHDRAWN")
    void testCompareScenarioWithNdc_UnitedStatesWithdrawnNonPartyHistoricalNdc() {
        DecarbonizationScenarioRequest req = DecarbonizationScenarioRequest.builder()
                .countryCode("USA")
                .displacedFuel("COAL")
                .replacementCleanSource("SOLAR_PV")
                .generationAmount(50.0)
                .generationUnit("TWH")
                .build();

        DecarbonizationScenarioResponse scenario = simulatorService.calculateScenario(req);
        // Explicitly evaluating historical NDC USA-2035-T1
        ScenarioNdcComparisonDto comp = policyService.compareScenarioWithNdc(scenario, "USA-2035-T1");

        assertNotNull(comp);
        assertEquals("USA", comp.getCountryCode());
        assertFalse(comp.isHasPowerSectorTarget());
        assertEquals("WITHDRAWN_TREATY_NON_PARTY", comp.getAlignmentStatus());
        assertEquals("NON_PARTY_TREATY_WITHDRAWN", comp.getComparabilityStatus());
        assertNull(comp.getCleanShareDistanceToTargetPct());
        assertNull(comp.getTargetPowerCapacitySharePct());
        assertNull(comp.getTargetPowerCleanGenerationSharePct());
        assertEquals("OFFICIAL_NDC_COMMITMENT", comp.getComparedTargetClassification());
        assertEquals("HISTORICAL_SUPERSEDED_OR_WITHDRAWN", comp.getComparedTargetCurrencyStatus());
        assertTrue(comp.getMethodologicalCaveats().stream().anyMatch(c -> c.contains("Treaty Non-Party Status")));
    }

    @Test
    @DisplayName("5c. US scenario comparison against domestic policy target (EO 14057) enables valid generation comparison with non-party guardrails")
    void testCompareScenarioWithNdc_UnitedStatesDomesticPolicyTargetComparable() {
        DecarbonizationScenarioRequest req = DecarbonizationScenarioRequest.builder()
                .countryCode("USA")
                .displacedFuel("COAL")
                .replacementCleanSource("SOLAR_PV")
                .generationAmount(50.0)
                .generationUnit("TWH")
                .build();

        DecarbonizationScenarioResponse scenario = simulatorService.calculateScenario(req);
        // Evaluating domestic power sector target (default or with USA-DOM-CFE)
        ScenarioNdcComparisonDto comp = policyService.compareScenarioWithNdc(scenario);

        assertNotNull(comp);
        assertEquals("USA", comp.getCountryCode());
        assertTrue(comp.isHasPowerSectorTarget(), "Domestic clean electricity target applies to power sector");
        assertEquals("GENERATION_METRIC_COMPARABLE", comp.getAlignmentStatus());
        assertEquals("GENERATION_METRIC_COMPARABLE", comp.getComparabilityStatus());
        assertEquals("Carbon Pollution-Free Electricity Goal (EO 14057)", comp.getComparedTargetTitle());
        assertEquals("DOMESTIC_POLICY_LAW", comp.getComparedTargetClassification(), "Must NOT be misrepresented as an active treaty commitment");
        assertEquals("STATUTORY_DOMESTIC", comp.getComparedTargetCurrencyStatus());
        assertEquals(100.0, comp.getTargetPowerCleanGenerationSharePct());
        assertNotNull(comp.getCleanShareDistanceToTargetPct(), "Distance must be calculated for comparable generation metric");
        assertEquals(100.0 - comp.getSimulatedCleanSharePct(), comp.getCleanShareDistanceToTargetPct(), 0.001);

        // Verification of caveats: Must include both Domestic Policy Authority and Treaty Non-Party Notice
        assertTrue(comp.getMethodologicalCaveats().stream().anyMatch(c -> c.contains("Domestic Policy Authority")));
        assertTrue(comp.getMethodologicalCaveats().stream().anyMatch(c -> c.contains("Treaty Non-Party Notice")));
        assertTrue(comp.getScopeMismatchExplanation().contains("It is NOT an active UNFCCC Paris Agreement treaty commitment"));
    }

    @Test
    @DisplayName("6. Comparison engine strictly prevents comparing capacity share (GW) with generation share (TWh)")
    void testCompareScenarioWithNdc_StrictCapacityVsGenerationSeparation() {
        DecarbonizationScenarioRequest req = DecarbonizationScenarioRequest.builder()
                .countryCode("IND")
                .displacedFuel("COAL")
                .replacementCleanSource("SOLAR_PV")
                .generationAmount(50.0)
                .generationUnit("TWH")
                .build();

        DecarbonizationScenarioResponse scenario = simulatorService.calculateScenario(req);
        ScenarioNdcComparisonDto comp = policyService.compareScenarioWithNdc(scenario);

        assertNotNull(comp);
        assertEquals("IND", comp.getCountryCode());
        assertTrue(comp.isHasPowerSectorTarget());

        // Capacity metric must be in targetPowerCapacitySharePct, NOT targetPowerCleanGenerationSharePct
        assertEquals(60.0, comp.getTargetPowerCapacitySharePct(), "2035 non-fossil capacity target is 60%");
        assertNull(comp.getTargetPowerCleanGenerationSharePct(), "Generation share target must be null for capacity targets");
        assertNull(comp.getCleanShareDistanceToTargetPct(), "Distance to target must be NULL when comparing capacity with generation!");

        assertEquals("METRIC_MISMATCH_CAPACITY_VS_GENERATION", comp.getAlignmentStatus());
        assertEquals("NON_COMPARABLE_CAPACITY_VS_GENERATION", comp.getComparabilityStatus());
        assertTrue(comp.getScopeMismatchExplanation().contains("Direct distance calculation is suppressed to avoid misleading comparisons"));
        assertTrue(comp.getMethodologicalCaveats().stream().anyMatch(c -> c.contains("Emissions Boundary Rule")));
    }

    @Test
    @DisplayName("7. Scenario comparison with generation-target country calculates like-for-like progression and notes domestic law classification")
    void testCompareScenarioWithNdc_GenerationTargetComparable() {
        DecarbonizationScenarioRequest req = DecarbonizationScenarioRequest.builder()
                .countryCode("DEU")
                .displacedFuel("COAL")
                .replacementCleanSource("WIND_ONSHORE")
                .generationAmount(20.0)
                .generationUnit("TWH")
                .build();

        DecarbonizationScenarioResponse scenario = simulatorService.calculateScenario(req);
        ScenarioNdcComparisonDto comp = policyService.compareScenarioWithNdc(scenario);

        assertNotNull(comp);
        assertEquals("DEU", comp.getCountryCode());
        assertTrue(comp.isHasPowerSectorTarget());
        assertEquals("GENERATION_METRIC_COMPARABLE", comp.getAlignmentStatus());
        assertEquals("GENERATION_METRIC_COMPARABLE", comp.getComparabilityStatus());
        assertEquals(80.0, comp.getTargetPowerCleanGenerationSharePct());
        assertNotNull(comp.getCleanShareDistanceToTargetPct());
        assertEquals("DOMESTIC_POLICY_LAW", comp.getComparedTargetClassification());
    }

    @Test
    @DisplayName("8. Validation handles invalid inputs safely")
    void testValidation_ErrorsHandledSafely() {
        assertThrows(SimulatorValidationException.class, () -> policyService.getNdcProfile(null));
        assertThrows(SimulatorValidationException.class, () -> policyService.getNdcProfile(""));
        assertThrows(SimulatorValidationException.class, () -> policyService.getNdcProfile("XYZ"));
        assertThrows(SimulatorValidationException.class, () -> policyService.getInterventionById(null));
        assertThrows(SimulatorValidationException.class, () -> policyService.getInterventionById("UNSUPPORTED"));
        assertThrows(SimulatorValidationException.class, () -> policyService.compareScenarioWithNdc(null));
    }

    @Test
    @DisplayName("9. Indonesia profile incorporates 2025 Second NDC (SNDC), 2019 baseline, and preserves 2022 targets")
    void testNdcCurrentness_IndonesiaSecondNdc() {
        CountryNdcProfileDto idn = policyService.getNdcProfile("IDN");
        assertEquals("Indonesia", idn.getCountryName());
        assertEquals("2025-10-27", idn.getLatestSubmissionDate());
        assertEquals("Second NDC (SNDC: 2031–2035 Horizon)", idn.getActiveNdcCycle());
        assertEquals(2035, idn.getTargetYearPrimary());
        assertEquals("PARTY_ACTIVE", idn.getTreatyParticipationStatus());

        // Active 2035 absolute cap
        NdcTargetDto t2035 = idn.getTargets().stream().filter(t -> "IDN-2035-T1".equals(t.getId())).findFirst().orElseThrow();
        assertEquals("FIXED_EMISSIONS_RANGE", t2035.getTargetType());
        assertEquals("ACTIVE_LATEST_COMMITMENT", t2035.getTargetCurrencyStatus());
        assertEquals(2019, t2035.getBaselineYear());
        assertEquals("1.26 to 1.49 Gt CO2e", t2035.getTargetRange());

        // 2030 peaking benchmark
        NdcTargetDto tPeak = idn.getTargets().stream().filter(t -> "IDN-2030-PEAK".equals(t.getId())).findFirst().orElseThrow();
        assertEquals("ACTIVE_LATEST_COMMITMENT", tPeak.getTargetCurrencyStatus());
        assertEquals("1.35 to 1.49 Gt CO2e", tPeak.getTargetRange());

        // Historical 2022 Enhanced NDC
        NdcTargetDto tHist = idn.getTargets().stream().filter(t -> "IDN-2030-HIST".equals(t.getId())).findFirst().orElseThrow();
        assertEquals("HISTORICAL_INTERMEDIATE_TARGET", tHist.getTargetCurrencyStatus());
        assertEquals(31.89, tHist.getTargetValue());
        assertEquals(43.20, tHist.getTargetValueConditional());

        // JETP 44% RE target
        NdcTargetDto tJetp = idn.getTargets().stream().filter(t -> "IDN-DOM-JETP".equals(t.getId())).findFirst().orElseThrow();
        assertEquals("RENEWABLE_SHARE_GENERATION", tJetp.getTargetType());
        assertEquals(44.0, tJetp.getTargetValue());
    }

    @Test
    @DisplayName("10. Country registry reconciles exactly 16 supported countries and excludes Mexico")
    void testSupportedCountryCount_Exactly16AndNoMexico() {
        List<CountryNdcProfileDto> all = policyService.getAllNdcProfiles();
        assertEquals(16, all.size(), "Exact supported country count must be 16");

        List<String> codes = all.stream().map(CountryNdcProfileDto::getCountryCode).sorted().toList();
        List<String> expectedCodes = List.of(
                "AUS", "BRA", "CAN", "CHN", "DEU", "ESP", "FRA", "GBR",
                "IDN", "IND", "ITA", "JPN", "POL", "USA", "VNM", "ZAF"
        );
        assertEquals(expectedCodes, codes);

        // Explicit regression test: Mexico must NOT be present in NDC registry or simulator
        assertFalse(codes.contains("MEX"), "Mexico must not be in NDC registry");
        assertThrows(SimulatorValidationException.class, () -> policyService.getNdcProfile("MEX"));
        assertThrows(SimulatorValidationException.class, () -> simulatorService.getCountryProfile("MEX"));

        // Ensure every profile has verified treaty status and provenance
        for (CountryNdcProfileDto p : all) {
            assertNotNull(p.getTreatyParticipationStatus(), "Treaty status required for " + p.getCountryCode());
            assertNotNull(p.getVerificationDate(), "Verification date required for " + p.getCountryCode());
            assertNotNull(p.getProvenance(), "Provenance required for " + p.getCountryCode());
            assertEquals("VERIFIED_SOURCE_OBSERVATION", p.getProvenance().getStatus());
        }
    }

    @Test
    @DisplayName("11. China and South Africa profiles reflect verified October/November 2025 NDC submissions")
    void testNdcCurrentness_ChinaAndSouthAfrica2025Submissions() {
        CountryNdcProfileDto chn = policyService.getNdcProfile("CHN");
        assertEquals("2025-11-03", chn.getLatestSubmissionDate());
        assertEquals("NDC 3.0 (2035 Horizon)", chn.getActiveNdcCycle());
        assertEquals(2035, chn.getTargetYearPrimary());
        NdcTargetDto chn2035 = chn.getTargets().stream().filter(t -> "CHN-2035-T1".equals(t.getId())).findFirst().orElseThrow();
        assertEquals("ACTIVE_LATEST_COMMITMENT", chn2035.getTargetCurrencyStatus());
        assertEquals("7% to 10% below peak", chn2035.getTargetRange());

        CountryNdcProfileDto zaf = policyService.getNdcProfile("ZAF");
        assertEquals("2025-10-15", zaf.getLatestSubmissionDate());
        assertEquals("Second NDC (2031–2035 Horizon)", zaf.getActiveNdcCycle());
        assertEquals(2035, zaf.getTargetYearPrimary());
        NdcTargetDto zaf2035 = zaf.getTargets().stream().filter(t -> "ZAF-2035-T1".equals(t.getId())).findFirst().orElseThrow();
        assertEquals("ACTIVE_LATEST_COMMITMENT", zaf2035.getTargetCurrencyStatus());
        assertEquals("320 to 380 Mt CO2e", zaf2035.getTargetRange());
    }
}
