package com.carbonscope.controller;

import com.carbonscope.service.EmissionCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthController {

    private final EmissionCacheService cacheService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> checkHealth() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "version", "2.0.0",
                "engine", "CarbonScope Multi-Source Carbon Intelligence Engine",
                "activeDataSources", List.of(
                        "Climate TRACE (Satellite & AI Inventory)",
                        "Global Carbon Project & Our World in Data (Per-Capita Consensus)",
                        "Ember Climate (Electricity Sector & Carbon Intensity)",
                        "EDGAR (European Commission JRC)",
                        "IPCC AR6 GWP Standards"
                ),
                "cache", cacheService.getCacheStats(),
                "serverTime", Instant.now().toString()
        ));
    }
}