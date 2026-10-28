package com.carbonscope.controller;

import com.carbonscope.dto.simulator.DecarbonizationScenarioRequest;
import com.carbonscope.service.DecarbonizationSimulatorService;
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

class DecarbonizationSimulatorControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        ReliableCarbonSourcesService reliableSources = new ReliableCarbonSourcesService();
        DecarbonizationSimulatorService service = new DecarbonizationSimulatorService(reliableSources);
        DecarbonizationSimulatorController controller = new DecarbonizationSimulatorController(service);

        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("GET /api/simulator/countries returns supported country electricity profiles")
    void testGetSupportedCountries() throws Exception {
        mockMvc.perform(get("/api/simulator/countries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(16))))
                .andExpect(jsonPath("$[0].countryCode", notNullValue()))
                .andExpect(jsonPath("$[0].dataSource", containsString("Ember")));
    }

    @Test
    @DisplayName("GET /api/simulator/baseline/IND returns India baseline electricity data")
    void testGetCountryBaseline() throws Exception {
        mockMvc.perform(get("/api/simulator/baseline/IND"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.countryCode", is("IND")))
                .andExpect(jsonPath("$.countryName", is("India")))
                .andExpect(jsonPath("$.totalGenerationTwh", is(1918.26)))
                .andExpect(jsonPath("$.gridCarbonIntensityGco2PerKwh", is(713.72)))
                .andExpect(jsonPath("$.accountingBoundary", is("LIFECYCLE_GHG_CO2E")))
                .andExpect(jsonPath("$.reconciliationStatus", containsString("INTERNAL_CATEGORY_RECONCILED")));
    }

    @Test
    @DisplayName("POST /api/simulator/calculate executes operational stack scenario by default with simulated metrics unavailable")
    void testCalculateScenarioOperationalDefault() throws Exception {
        DecarbonizationScenarioRequest req = DecarbonizationScenarioRequest.builder()
                .countryCode("USA")
                .displacedFuel("COAL")
                .replacementCleanSource("SOLAR_PV")
                .generationAmount(50.0)
                .generationUnit("TWH")
                .build();

        mockMvc.perform(post("/api/simulator/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.countryCode", is("USA")))
                .andExpect(jsonPath("$.accountingBoundary", is("OPERATIONAL_SCOPE_1")))
                .andExpect(jsonPath("$.impact.grossAvoidedEmissionsMt", is(47.50)))
                .andExpect(jsonPath("$.impact.cleanReplacementLifecycleEmissionsMt", is(0.0)))
                .andExpect(jsonPath("$.impact.netAvoidedEmissionsMt", is(47.50)))
                .andExpect(jsonPath("$.scenario.simulatedPowerSectorEmissionsMt", nullValue()))
                .andExpect(jsonPath("$.scenario.simulatedGridCarbonIntensityGco2PerKwh", nullValue()))
                .andExpect(jsonPath("$.impact.simulatedMetricsUnavailableReason", containsString("UNAVAILABLE")))
                .andExpect(jsonPath("$.provenance.status", is("DETERMINISTIC_MODEL_RESULT")));
    }

    @Test
    @DisplayName("POST /api/simulator/calculate executes lifecycle scenario when requested")
    void testCalculateScenarioLifecycleMode() throws Exception {
        DecarbonizationScenarioRequest req = DecarbonizationScenarioRequest.builder()
                .countryCode("USA")
                .displacedFuel("COAL")
                .replacementCleanSource("SOLAR_PV")
                .generationAmount(50.0)
                .generationUnit("TWH")
                .accountingBoundary("LIFECYCLE_SCOPE_1_2_3")
                .build();

        mockMvc.perform(post("/api/simulator/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.countryCode", is("USA")))
                .andExpect(jsonPath("$.accountingBoundary", is("LIFECYCLE_SCOPE_1_2_3")))
                .andExpect(jsonPath("$.impact.grossAvoidedEmissionsMt", is(41.0)))
                .andExpect(jsonPath("$.impact.cleanReplacementLifecycleEmissionsMt", is(2.4)))
                .andExpect(jsonPath("$.impact.netAvoidedEmissionsMt", is(38.6)))
                .andExpect(jsonPath("$.scenario.simulatedPowerSectorEmissionsMt", nullValue()))
                .andExpect(jsonPath("$.scenario.simulatedGridCarbonIntensityGco2PerKwh", nullValue()))
                .andExpect(jsonPath("$.impact.simulatedMetricsUnavailableReason", containsString("UNAVAILABLE")))
                .andExpect(jsonPath("$.provenance.status", is("DETERMINISTIC_MODEL_RESULT")));
    }

    @Test
    @DisplayName("POST /api/simulator/calculate with physically impossible fuel displacement returns 400 Bad Request")
    void testCalculateScenarioPhysicalBoundRejection() throws Exception {
        // GBR only has 3.0 TWh coal
        DecarbonizationScenarioRequest req = DecarbonizationScenarioRequest.builder()
                .countryCode("GBR")
                .displacedFuel("COAL")
                .replacementCleanSource("WIND_OFFSHORE")
                .generationAmount(20.0)
                .generationUnit("TWH")
                .build();

        mockMvc.perform(post("/api/simulator/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Simulator Validation Error")))
                .andExpect(jsonPath("$.message", containsString("exceeds maximum available Coal generation")));
    }

    @Test
    @DisplayName("POST /api/simulator/calculate with unsupported country returns 400 Bad Request")
    void testCalculateScenarioUnsupportedCountryRejection() throws Exception {
        DecarbonizationScenarioRequest req = DecarbonizationScenarioRequest.builder()
                .countryCode("ZZZ")
                .displacedFuel("COAL")
                .replacementCleanSource("SOLAR_PV")
                .generationAmount(10.0)
                .generationUnit("TWH")
                .build();

        mockMvc.perform(post("/api/simulator/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Simulator Validation Error")))
                .andExpect(jsonPath("$.message", containsString("not supported")));
    }

    @Test
    @DisplayName("POST /api/simulator/calculate with multi-intervention payload returns composite results and itemized interventions")
    void testCalculateCompositeScenario_MultipleInterventionsAndConstraints() throws Exception {
        com.carbonscope.dto.simulator.ScenarioInterventionDto solar = com.carbonscope.dto.simulator.ScenarioInterventionDto.builder()
                .id("solar-1")
                .cleanSource("SOLAR_PV")
                .targetDisplacedFuel("COAL")
                .generationAmount(30.0)
                .generationUnit("TWH")
                .build();

        com.carbonscope.dto.simulator.ScenarioInterventionDto wind = com.carbonscope.dto.simulator.ScenarioInterventionDto.builder()
                .id("wind-1")
                .cleanSource("WIND_ONSHORE")
                .targetDisplacedFuel("FOSSIL_GAS")
                .generationAmount(20.0)
                .generationUnit("TWH")
                .build();

        com.carbonscope.dto.simulator.ScenarioConstraintsDto constraints = com.carbonscope.dto.simulator.ScenarioConstraintsDto.builder()
                .minResidualCoalTwh(500.0)
                .build();

        DecarbonizationScenarioRequest req = DecarbonizationScenarioRequest.builder()
                .countryCode("USA")
                .accountingBoundary("OPERATIONAL_SCOPE_1")
                .interventions(java.util.List.of(solar, wind))
                .constraints(constraints)
                .build();

        mockMvc.perform(post("/api/simulator/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.countryCode", is("USA")))
                .andExpect(jsonPath("$.scenario.totalRequestedCleanGenerationTwh", is(50.0)))
                .andExpect(jsonPath("$.scenario.totalDisplacedFossilGenerationTwh", is(50.0)))
                .andExpect(jsonPath("$.scenario.totalUnusedCleanGenerationTwh", is(0.0)))
                .andExpect(jsonPath("$.impact.itemizedInterventions", hasSize(2)))
                .andExpect(jsonPath("$.impact.itemizedInterventions[0].cleanSource", is("SOLAR_PV")))
                .andExpect(jsonPath("$.impact.itemizedInterventions[0].targetDisplacedFuel", is("COAL")))
                .andExpect(jsonPath("$.impact.itemizedInterventions[1].cleanSource", is("WIND_ONSHORE")))
                .andExpect(jsonPath("$.impact.itemizedInterventions[1].targetDisplacedFuel", is("FOSSIL_GAS")))
                .andExpect(jsonPath("$.scenario.constraintsApplied", is(true)))
                .andExpect(jsonPath("$.impact.sensitivityInterval", notNullValue()));
    }
}
