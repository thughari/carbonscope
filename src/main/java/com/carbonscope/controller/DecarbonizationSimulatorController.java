package com.carbonscope.controller;

import com.carbonscope.dto.simulator.CountryElectricityProfileDto;
import com.carbonscope.dto.simulator.DecarbonizationScenarioRequest;
import com.carbonscope.dto.simulator.DecarbonizationScenarioResponse;
import com.carbonscope.exception.SimulatorValidationException;
import com.carbonscope.service.DecarbonizationSimulatorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/simulator")
@RequiredArgsConstructor
public class DecarbonizationSimulatorController {

    private final DecarbonizationSimulatorService simulatorService;

    @GetMapping("/countries")
    public List<CountryElectricityProfileDto> getSupportedCountries() {
        return simulatorService.getSupportedCountries();
    }

    @GetMapping("/baseline/{countryCode}")
    public CountryElectricityProfileDto getCountryBaseline(@PathVariable String countryCode) {
        return simulatorService.getCountryProfile(countryCode);
    }

    @PostMapping("/calculate")
    public DecarbonizationScenarioResponse calculateScenario(@RequestBody DecarbonizationScenarioRequest request) {
        return simulatorService.calculateScenario(request);
    }

    @ExceptionHandler(SimulatorValidationException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(SimulatorValidationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "error", "Simulator Validation Error",
                "message", ex.getMessage(),
                "timestamp", Instant.now().toString()
        ));
    }
}
