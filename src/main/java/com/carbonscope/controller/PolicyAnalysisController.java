package com.carbonscope.controller;

import com.carbonscope.dto.policy.*;
import com.carbonscope.dto.simulator.DecarbonizationScenarioResponse;
import com.carbonscope.exception.SimulatorValidationException;
import com.carbonscope.service.PolicyAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/policy")
@RequiredArgsConstructor
public class PolicyAnalysisController {

    private final PolicyAnalysisService policyService;

    @GetMapping("/interventions")
    public List<InterventionEvidenceDto> getAllInterventions() {
        return policyService.getAllInterventions();
    }

    @GetMapping("/interventions/{techId}")
    public InterventionEvidenceDto getInterventionById(@PathVariable String techId) {
        return policyService.getInterventionById(techId);
    }

    @GetMapping("/ndc")
    public List<CountryNdcProfileDto> getAllNdcProfiles() {
        return policyService.getAllNdcProfiles();
    }

    @GetMapping("/ndc/{countryCode}")
    public CountryNdcProfileDto getNdcProfile(@PathVariable String countryCode) {
        return policyService.getNdcProfile(countryCode);
    }

    @PostMapping("/compare-scenario")
    public ScenarioNdcComparisonDto compareScenarioWithNdc(
            @RequestBody DecarbonizationScenarioResponse scenario,
            @RequestParam(required = false) String targetId) {
        return policyService.compareScenarioWithNdc(scenario, targetId);
    }

    @GetMapping("/overview/{countryCode}")
    public PolicyComparisonResponse getPolicyOverview(@PathVariable String countryCode) {
        return policyService.getPolicyComparison(countryCode, null);
    }

    @ExceptionHandler(SimulatorValidationException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(SimulatorValidationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "error", "Policy Analysis Validation Error",
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }
}
