package com.carbonscope.controller;

import com.carbonscope.dto.*;
import com.carbonscope.service.EmissionAnalyticsService;
import com.carbonscope.service.ReliableCarbonSourcesService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/emissions")
@RequiredArgsConstructor
public class EmissionController {

    private final EmissionAnalyticsService service;
    private final ReliableCarbonSourcesService reliableSourcesService;

    @GetMapping("/overview")
    public EmissionsOverviewResponse getOverview(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String continent,
            @RequestParam(required = false) String gas
    ) {
        return service.getEmissionsOverview(year, country, continent, gas);
    }

    @GetMapping("/sources-info")
    public SourcesInfoResponse getSourcesInfo() {
        return reliableSourcesService.getSourcesInfoResponse();
    }

    @GetMapping("/sectors")
    public SectorSummaryResponse getSectors(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String continent,
            @RequestParam(required = false) String gas,
            @RequestParam(required = false) Integer limit
    ) throws Exception {
        return service.getSectorSummary(year, country, continent, gas, limit);
    }

    @GetMapping("/subsectors")
    public SubsectorSummaryResponse getSubsectors(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String continent,
            @RequestParam String sector,
            @RequestParam(required = false) String gas,
            @RequestParam(required = false) Integer limit
    ) throws Exception {
        return service.getSubsectorSummary(year, country, continent, sector, gas, limit);
    }

    @GetMapping("/countries")
    public CountrySummaryResponse getCountries(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String continent,
            @RequestParam(required = false) String sector,
            @RequestParam(required = false) String gas,
            @RequestParam(required = false) Integer limit
    ) throws Exception {
        return service.getCountrySummary(year, continent, sector, gas, limit);
    }

    @GetMapping("/continents")
    public ContinentSummaryResponse getContinents(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String sector,
            @RequestParam(required = false) String gas,
            @RequestParam(required = false) Integer limit
    ) throws Exception {
        return service.getContinentSummary(year, sector, gas, limit);
    }

    @GetMapping("/sources")
    public SourcesResponse getSources(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String continent,
            @RequestParam(required = false) String sector,
            @RequestParam(required = false) String subsector,
            @RequestParam(required = false) String gas,
            @RequestParam(required = false) Integer limit
    ) throws Exception {
        return service.getSources(year, country, continent, sector, subsector, gas, limit);
    }
}
