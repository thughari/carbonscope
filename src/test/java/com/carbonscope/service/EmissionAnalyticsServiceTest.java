package com.carbonscope.service;

import com.carbonscope.client.ClimateTraceClient;
import com.carbonscope.dto.*;
import com.carbonscope.model.CountryEmissionsRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmissionAnalyticsServiceTest {

    @Mock
    private ClimateTraceClient client;

    private ReliableCarbonSourcesService reliableSources;
    private EmissionCacheService cacheService;
    private EmissionAnalyticsService service;

    @BeforeEach
    void setUp() {
        reliableSources = new ReliableCarbonSourcesService();
        cacheService = new EmissionCacheService();
        service = new EmissionAnalyticsService(client, reliableSources, cacheService);
    }

    @Test
    @DisplayName("Unsupported reporting year produces explicit DATA_UNAVAILABLE state without synthetic values")
    void testUnsupportedReportingYearProducesUnavailableState() {
        int unsupportedYear = 2035;

        EmissionsOverviewResponse response = service.getEmissionsOverview(unsupportedYear, "IND", null, "co2e_100yr");

        assertNotNull(response);
        assertEquals("DATA_UNAVAILABLE", response.getDataSourceStatus());
        assertEquals(0.0, response.getTotalEmissionsMt(), 0.001);
        assertEquals(0.0, response.getTotalEmissionsTonnes(), 0.001);
        assertEquals("Data Unavailable", response.getDataReliability());
        assertNotNull(response.getProvenance());
        assertEquals("DATA_UNAVAILABLE", response.getProvenance().getStatus());
        assertFalse(response.getProvenance().isEmpirical());
        assertTrue(response.getDataQualityNotes().contains("outside empirical satellite observation coverage"));
        assertNotEquals("98.4% Empirical Satellite + Scientific Consensus", response.getDataReliability());

        // Ensure external API wasn't even called for out-of-range year
        verify(client, never()).fetchCountryEmissions(eq(unsupportedYear), any());
    }

    @Test
    @DisplayName("Historical years before 2015 produce explicit DATA_UNAVAILABLE state")
    void testPre2015YearProducesUnavailableState() {
        int oldYear = 2010;

        EmissionsOverviewResponse response = service.getEmissionsOverview(oldYear, "USA", null, "co2e_100yr");

        assertNotNull(response);
        assertEquals("DATA_UNAVAILABLE", response.getDataSourceStatus());
        assertEquals(0.0, response.getTotalEmissionsMt(), 0.001);
        assertNotNull(response.getProvenance());
        assertEquals("DATA_UNAVAILABLE", response.getProvenance().getStatus());
        assertFalse(response.getProvenance().isEmpirical());
    }

    @Test
    @DisplayName("Live API observation populates transparent LIVE_API_OBSERVATION provenance")
    void testLiveApiObservationProvenance() {
        int year = 2024;
        String country = "IND";

        CountryEmissionsRecord record = new CountryEmissionsRecord();
        record.setCountry("IND");
        record.setRank(3);
        record.setEmissions(Map.of("co2e_100yr", 4_165_000_000.0));

        when(client.fetchCountryEmissions(eq(year), eq(country))).thenReturn(record);
        when(client.fetchCountryEmissions(eq(year), isNull())).thenReturn(record);

        EmissionsOverviewResponse response = service.getEmissionsOverview(year, country, null, "co2e_100yr");

        assertNotNull(response);
        assertEquals("LIVE_API_OBSERVATION", response.getDataSourceStatus());
        assertEquals(4165.0, response.getTotalEmissionsMt(), 0.1);
        assertEquals(3, response.getCountryRank());
        assertEquals("Empirical Satellite Inventory (Climate TRACE v6)", response.getDataReliability());
        assertNotNull(response.getProvenance());
        assertEquals("LIVE_API_OBSERVATION", response.getProvenance().getStatus());
        assertTrue(response.getProvenance().isEmpirical());
        assertEquals("Climate TRACE v6 Satellite & AI Observation", response.getProvenance().getSourceName());
    }

    @Test
    @DisplayName("When API fails for calibrated baseline year 2024, fallback is explicitly marked STATIC_REFERENCE_BENCHMARK")
    void testCalibratedBenchmarkFallbackProvenance() {
        int year = 2024;
        String country = "IND";

        when(client.fetchCountryEmissions(eq(year), eq(country))).thenReturn(null);
        when(client.fetchCountryEmissions(eq(year), isNull())).thenReturn(null);

        EmissionsOverviewResponse response = service.getEmissionsOverview(year, country, null, "co2e_100yr");

        assertNotNull(response);
        assertEquals("STATIC_REFERENCE_BENCHMARK", response.getDataSourceStatus());
        assertEquals(4165.4, response.getTotalEmissionsMt(), 0.1);
        assertEquals("Calibrated Reference Benchmark (Ember / GCP Consensus)", response.getDataReliability());
        assertNotNull(response.getProvenance());
        assertEquals("STATIC_REFERENCE_BENCHMARK", response.getProvenance().getStatus());
        assertFalse(response.getProvenance().isEmpirical());
        assertTrue(response.getDataQualityNotes().contains("calibrated 2024 consensus benchmark"));
    }

    @Test
    @DisplayName("Sector summary returns empty map and DATA_UNAVAILABLE when no empirical data exists, avoiding synthetic ratios")
    void testSectorSummaryAvoidsSyntheticRatios() throws Exception {
        when(client.fetchAssetSectorEmissions(any(), any(), any())).thenReturn(Collections.emptyList());
        when(client.fetchSources(any(), any(), any(), any(), any(), any(), any())).thenReturn(Collections.emptyList());

        SectorSummaryResponse response = service.getSectorSummary(2024, "ZZZ", null, "co2e_100yr", 100);

        assertNotNull(response);
        assertEquals("DATA_UNAVAILABLE", response.getDataSourceStatus());
        assertTrue(response.getSectorTotals().isEmpty(), "Sector totals must be empty rather than fabricated with synthetic 38%/24% ratios");
        assertNotNull(response.getProvenance());
        assertEquals("DATA_UNAVAILABLE", response.getProvenance().getStatus());
        assertFalse(response.getProvenance().isEmpirical());
    }

    @Test
    @DisplayName("Subsector summary returns empty map and DATA_UNAVAILABLE when no empirical assets exist, avoiding dummyBase generation")
    void testSubsectorSummaryAvoidsDummyBaseGeneration() throws Exception {
        when(client.fetchAssetSectorEmissions(any(), any(), any())).thenReturn(Collections.emptyList());
        when(client.fetchSources(any(), any(), any(), any(), any(), any(), any())).thenReturn(Collections.emptyList());

        SubsectorSummaryResponse response = service.getSubsectorSummary(2024, "ZZZ", null, "power", "co2e_100yr", 100);

        assertNotNull(response);
        assertEquals("DATA_UNAVAILABLE", response.getDataSourceStatus());
        assertTrue(response.getSubsectorTotals().isEmpty(), "Subsector totals must be empty rather than fabricated with dummyBase values");
        assertNotNull(response.getProvenance());
        assertEquals("DATA_UNAVAILABLE", response.getProvenance().getStatus());
        assertFalse(response.getProvenance().isEmpirical());
    }

    @Test
    @DisplayName("API timeout on non-baseline year produces explicit DATA_UNAVAILABLE without fabricated emissions")
    void testApiTimeoutNonBaselineYearProducesDataUnavailable() {
        int year = 2023;
        String country = "IND";

        when(client.fetchCountryEmissions(eq(year), eq(country)))
                .thenThrow(new RuntimeException("Connection timed out after 10000ms"));

        EmissionsOverviewResponse response = service.getEmissionsOverview(year, country, null, "co2e_100yr");

        assertNotNull(response);
        assertEquals("DATA_UNAVAILABLE", response.getDataSourceStatus());
        assertEquals(0.0, response.getTotalEmissionsMt(), 0.001);
        assertEquals(0.0, response.getTotalEmissionsTonnes(), 0.001);
        assertEquals("Data Unavailable", response.getDataReliability());
        assertEquals("UNAVAILABLE", response.getTemporalCompleteness());
        assertFalse(response.isPartialOrProvisional());
        assertNotNull(response.getProvenance());
        assertEquals("DATA_UNAVAILABLE", response.getProvenance().getStatus());
        assertFalse(response.getProvenance().isEmpirical());
    }

    @Test
    @DisplayName("API timeout on baseline year 2024 gracefully serves STATIC_REFERENCE_BENCHMARK with explicit notes")
    void testApiTimeoutBaselineYearServesStaticBenchmark() {
        int year = 2024;
        String country = "IND";

        when(client.fetchCountryEmissions(eq(year), eq(country)))
                .thenThrow(new RuntimeException("Connection timed out after 10000ms"));

        EmissionsOverviewResponse response = service.getEmissionsOverview(year, country, null, "co2e_100yr");

        assertNotNull(response);
        assertEquals("STATIC_REFERENCE_BENCHMARK", response.getDataSourceStatus());
        assertEquals(4165.4, response.getTotalEmissionsMt(), 0.1);
        assertEquals("Calibrated Reference Benchmark (Ember / GCP Consensus)", response.getDataReliability());
        assertEquals("CONSOLIDATED_ANNUAL", response.getTemporalCompleteness());
        assertFalse(response.isPartialOrProvisional());
        assertNotNull(response.getProvenance());
        assertEquals("STATIC_REFERENCE_BENCHMARK", response.getProvenance().getStatus());
        assertFalse(response.getProvenance().isEmpirical());
        assertTrue(response.getDataQualityNotes().contains("temporarily unavailable; serving calibrated 2024 consensus benchmark"));
    }

    @Test
    @DisplayName("Empty API response for non-baseline year produces explicit DATA_UNAVAILABLE state")
    void testEmptyApiResponseNonBaselineYearProducesDataUnavailable() {
        int year = 2022;
        String country = "BRA";

        when(client.fetchCountryEmissions(eq(year), eq(country))).thenReturn(null);

        EmissionsOverviewResponse response = service.getEmissionsOverview(year, country, null, "co2e_100yr");

        assertNotNull(response);
        assertEquals("DATA_UNAVAILABLE", response.getDataSourceStatus());
        assertEquals(0.0, response.getTotalEmissionsMt(), 0.001);
        assertEquals("UNAVAILABLE", response.getTemporalCompleteness());
        assertNull(response.getPerCapitaTonnes());
    }

    @Test
    @DisplayName("Year 2026 responses are explicitly flagged PARTIAL_YEAR_OBSERVATION and provisional in metadata")
    void testPartialYearSemantics2026() {
        int year = 2026;
        String country = "IND";

        CountryEmissionsRecord record = new CountryEmissionsRecord();
        record.setCountry("IND");
        record.setRank(3);
        record.setEmissions(Map.of("co2e_100yr", 2_482_000_000.0));

        when(client.fetchCountryEmissions(eq(year), eq(country))).thenReturn(record);
        when(client.fetchCountryEmissions(eq(year), isNull())).thenReturn(record);

        EmissionsOverviewResponse response = service.getEmissionsOverview(year, country, null, "co2e_100yr");

        assertNotNull(response);
        assertEquals("LIVE_API_OBSERVATION", response.getDataSourceStatus());
        assertEquals(2482.0, response.getTotalEmissionsMt(), 0.1);
        assertEquals("PARTIAL_YEAR_OBSERVATION", response.getTemporalCompleteness());
        assertTrue(response.isPartialOrProvisional());
        assertTrue(response.getDataReliability().contains("Partial Year YTD"));
        assertTrue(response.getDataQualityNotes().contains("7 months elapsed"));
        assertNotNull(response.getProvenance());
        assertEquals("PARTIAL_YEAR_OBSERVATION", response.getProvenance().getTemporalCompleteness());
        assertTrue(response.getProvenance().isPartialOrProvisional());
    }

    @Test
    @DisplayName("Year 2025 responses are explicitly flagged PROVISIONAL_ANNUAL in metadata")
    void testProvisionalAnnualSemantics2025() {
        int year = 2025;
        String country = "IND";

        CountryEmissionsRecord record = new CountryEmissionsRecord();
        record.setCountry("IND");
        record.setRank(3);
        record.setEmissions(Map.of("co2e_100yr", 4_182_000_000.0));

        when(client.fetchCountryEmissions(eq(year), eq(country))).thenReturn(record);
        when(client.fetchCountryEmissions(eq(year), isNull())).thenReturn(record);

        EmissionsOverviewResponse response = service.getEmissionsOverview(year, country, null, "co2e_100yr");

        assertNotNull(response);
        assertEquals("LIVE_API_OBSERVATION", response.getDataSourceStatus());
        assertEquals(4182.0, response.getTotalEmissionsMt(), 0.1);
        assertEquals("PROVISIONAL_ANNUAL", response.getTemporalCompleteness());
        assertTrue(response.isPartialOrProvisional());
        assertTrue(response.getDataReliability().contains("Provisional Annual"));
        assertNotNull(response.getProvenance());
        assertEquals("PROVISIONAL_ANNUAL", response.getProvenance().getTemporalCompleteness());
        assertTrue(response.getProvenance().isPartialOrProvisional());
    }

    @Test
    @DisplayName("Baseline year 2024 is marked CONSOLIDATED_ANNUAL and not provisional")
    void testConsolidatedAnnualSemantics2024() {
        int year = 2024;
        String country = "IND";

        CountryEmissionsRecord record = new CountryEmissionsRecord();
        record.setCountry("IND");
        record.setRank(3);
        record.setEmissions(Map.of("co2e_100yr", 4_165_000_000.0));

        when(client.fetchCountryEmissions(eq(year), eq(country))).thenReturn(record);
        when(client.fetchCountryEmissions(eq(year), isNull())).thenReturn(record);

        EmissionsOverviewResponse response = service.getEmissionsOverview(year, country, null, "co2e_100yr");

        assertNotNull(response);
        assertEquals("LIVE_API_OBSERVATION", response.getDataSourceStatus());
        assertEquals("CONSOLIDATED_ANNUAL", response.getTemporalCompleteness());
        assertFalse(response.isPartialOrProvisional());
        assertNotNull(response.getProvenance());
        assertEquals("CONSOLIDATED_ANNUAL", response.getProvenance().getTemporalCompleteness());
        assertFalse(response.getProvenance().isPartialOrProvisional());
    }
}
