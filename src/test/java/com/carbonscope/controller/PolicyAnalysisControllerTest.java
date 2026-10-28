package com.carbonscope.controller;

import com.carbonscope.dto.simulator.DecarbonizationScenarioRequest;
import com.carbonscope.dto.simulator.DecarbonizationScenarioResponse;
import com.carbonscope.service.DecarbonizationSimulatorService;
import com.carbonscope.service.PolicyAnalysisService;
import com.carbonscope.service.ReliableCarbonSourcesService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PolicyAnalysisControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private DecarbonizationSimulatorService simulatorService;

    @BeforeEach
    void setUp() {
        ReliableCarbonSourcesService reliableSources = new ReliableCarbonSourcesService();
        simulatorService = new DecarbonizationSimulatorService(reliableSources);
        PolicyAnalysisService policyService = new PolicyAnalysisService(simulatorService);
        PolicyAnalysisController controller = new PolicyAnalysisController(policyService);

        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        objectMapper = new ObjectMapper();
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false);
        org.springframework.http.converter.json.MappingJackson2HttpMessageConverter converter =
                new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(objectMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(converter)
                .build();
    }

    @Test
    @DisplayName("GET /api/policy/interventions returns 8 evidence-backed technologies")
    void testGetAllInterventions() throws Exception {
        mockMvc.perform(get("/api/policy/interventions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(8)))
                .andExpect(jsonPath("$[0].technologyId", notNullValue()))
                .andExpect(jsonPath("$[0].sourceCitation", containsString("IPCC")));
    }

    @Test
    @DisplayName("GET /api/policy/interventions/SOLAR_PV returns Solar PV details with unavailable LCOE")
    void testGetInterventionSolar() throws Exception {
        mockMvc.perform(get("/api/policy/interventions/SOLAR_PV"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.technologyId", is("SOLAR_PV")))
                .andExpect(jsonPath("$.lifecycleFactorMedianGco2PerKwh", is(48.0)))
                .andExpect(jsonPath("$.lcoeDataAvailable", is(false)))
                .andExpect(jsonPath("$.capacityFactorDataAvailable", is(false)));
    }

    @Test
    @DisplayName("GET /api/policy/ndc returns all 16 country NDC profiles")
    void testGetAllNdcProfiles() throws Exception {
        mockMvc.perform(get("/api/policy/ndc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(16)))
                .andExpect(jsonPath("$[?(@.countryCode == 'IND')].documentTitle", notNullValue()));
    }

    @Test
    @DisplayName("GET /api/policy/ndc/IND returns India's NDC 3.0 and historical targets")
    void testGetIndiaNdc() throws Exception {
        mockMvc.perform(get("/api/policy/ndc/IND"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.countryCode", is("IND")))
                .andExpect(jsonPath("$.latestSubmissionDate", is("2026-04-24")))
                .andExpect(jsonPath("$.activeNdcCycle", is("NDC 3.0 (2031–2035 Horizon)")))
                .andExpect(jsonPath("$.documentTitle", containsString("Third Nationally Determined Contribution")))
                .andExpect(jsonPath("$.targets", hasSize(greaterThanOrEqualTo(6))))
                .andExpect(jsonPath("$.targets[0].targetClassification", is("OFFICIAL_NDC_COMMITMENT")))
                .andExpect(jsonPath("$.targets[0].targetCurrencyStatus", is("ACTIVE_LATEST_COMMITMENT")));
    }

    @Test
    @DisplayName("GET /api/policy/ndc/INVALID returns 400 Bad Request with error explanation")
    void testGetInvalidNdc_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/policy/ndc/INVALID"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Policy Analysis Validation Error")))
                .andExpect(jsonPath("$.message", containsString("No verified UNFCCC NDC profile registered")));
    }

    @Test
    @DisplayName("POST /api/policy/compare-scenario evaluates simulated scenario against NDC and suppresses invalid distance calculation")
    void testCompareScenarioWithNdc() throws Exception {
        DecarbonizationScenarioRequest req = DecarbonizationScenarioRequest.builder()
                .countryCode("IND")
                .displacedFuel("COAL")
                .replacementCleanSource("SOLAR_PV")
                .generationAmount(50.0)
                .generationUnit("TWH")
                .build();

        DecarbonizationScenarioResponse scenario = simulatorService.calculateScenario(req);

        mockMvc.perform(post("/api/policy/compare-scenario")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(scenario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.countryCode", is("IND")))
                .andExpect(jsonPath("$.hasPowerSectorTarget", is(true)))
                .andExpect(jsonPath("$.alignmentStatus", is("METRIC_MISMATCH_CAPACITY_VS_GENERATION")))
                .andExpect(jsonPath("$.comparabilityStatus", is("NON_COMPARABLE_CAPACITY_VS_GENERATION")))
                .andExpect(jsonPath("$.targetPowerCapacitySharePct", is(60.0)))
                .andExpect(jsonPath("$.targetPowerCleanGenerationSharePct", nullValue()))
                .andExpect(jsonPath("$.cleanShareDistanceToTargetPct", nullValue()))
                .andExpect(jsonPath("$.scopeMismatchExplanation", containsString("Direct distance calculation is suppressed to avoid misleading comparisons")));
    }

    @Test
    @DisplayName("GET /api/policy/overview/IND returns complete overview response")
    void testGetPolicyOverview() throws Exception {
        mockMvc.perform(get("/api/policy/overview/IND"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.countryNdc.countryCode", is("IND")))
                .andExpect(jsonPath("$.interventions", hasSize(8)))
                .andExpect(jsonPath("$.methodologyNote", notNullValue()));
    }

    @Test
    @DisplayName("POST /api/policy/compare-scenario?targetId=USA-2035-T1 returns WITHDRAWN_TREATY_NON_PARTY alignment")
    void testCompareScenarioWithNdc_UsaWithdrawnNonParty() throws Exception {
        DecarbonizationScenarioRequest req = DecarbonizationScenarioRequest.builder()
                .countryCode("USA")
                .displacedFuel("COAL")
                .replacementCleanSource("WIND_ONSHORE")
                .generationAmount(50.0)
                .generationUnit("TWH")
                .build();

        DecarbonizationScenarioResponse scenario = simulatorService.calculateScenario(req);

        mockMvc.perform(post("/api/policy/compare-scenario")
                        .param("targetId", "USA-2035-T1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(scenario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.countryCode", is("USA")))
                .andExpect(jsonPath("$.alignmentStatus", is("WITHDRAWN_TREATY_NON_PARTY")))
                .andExpect(jsonPath("$.comparabilityStatus", is("NON_PARTY_TREATY_WITHDRAWN")))
                .andExpect(jsonPath("$.cleanShareDistanceToTargetPct", nullValue()))
                .andExpect(jsonPath("$.targetPowerCapacitySharePct", nullValue()))
                .andExpect(jsonPath("$.targetPowerCleanGenerationSharePct", nullValue()))
                .andExpect(jsonPath("$.comparedTargetClassification", is("OFFICIAL_NDC_COMMITMENT")));
    }

    @Test
    @DisplayName("POST /api/policy/compare-scenario for USA evaluates domestic clean electricity target (EO 14057)")
    void testCompareScenarioWithNdc_UsaDomesticPolicyTarget() throws Exception {
        DecarbonizationScenarioRequest req = DecarbonizationScenarioRequest.builder()
                .countryCode("USA")
                .displacedFuel("COAL")
                .replacementCleanSource("WIND_ONSHORE")
                .generationAmount(50.0)
                .generationUnit("TWH")
                .build();

        DecarbonizationScenarioResponse scenario = simulatorService.calculateScenario(req);

        mockMvc.perform(post("/api/policy/compare-scenario")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(scenario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.countryCode", is("USA")))
                .andExpect(jsonPath("$.hasPowerSectorTarget", is(true)))
                .andExpect(jsonPath("$.alignmentStatus", is("GENERATION_METRIC_COMPARABLE")))
                .andExpect(jsonPath("$.comparabilityStatus", is("GENERATION_METRIC_COMPARABLE")))
                .andExpect(jsonPath("$.comparedTargetClassification", is("DOMESTIC_POLICY_LAW")))
                .andExpect(jsonPath("$.targetPowerCleanGenerationSharePct", is(100.0)))
                .andExpect(jsonPath("$.cleanShareDistanceToTargetPct", notNullValue()));
    }
}
