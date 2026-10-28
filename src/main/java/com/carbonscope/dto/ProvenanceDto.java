package com.carbonscope.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProvenanceDto {
    /**
     * Provenance status:
     * - LIVE_API_OBSERVATION: Real observations returned directly from external satellite API
     * - STATIC_REFERENCE_BENCHMARK: Curated peer-reviewed benchmark (e.g. Ember 2024, GCP)
     * - MODELED_ESTIMATE: Statistically modeled or regionally aggregated
     * - DATA_UNAVAILABLE: No observations exist for the requested parameters
     */
    private String status;

    /**
     * Name of the verified primary data provider (e.g., "Climate TRACE v6", "Ember Climate 2024")
     */
    private String sourceName;

    /**
     * Technical methodology or observation protocol
     */
    private String methodology;

    /**
     * The verified reporting year of this observation
     */
    private Integer reportingYear;

    /**
     * Temporal completeness:
     * - CONSOLIDATED_ANNUAL: Full 12-month annual accounting cycle (<= 2024)
     * - PROVISIONAL_ANNUAL: Preliminary 12-month consolidated estimate (2025)
     * - PARTIAL_YEAR_OBSERVATION: Incomplete / Year-to-date observation window (2026)
     * - UNAVAILABLE: No observations
     */
    private String temporalCompleteness;

    /**
     * True if the observation is provisional or partial-year (e.g., 2025/2026)
     */
    private boolean isPartialOrProvisional;

    /**
     * True if empirical measurement, false if derived/benchmark
     */
    private boolean empirical;

    /**
     * Transparent scientific quality notes and boundary disclosures
     */
    private String qualityNotes;

    /**
     * Explicit warning when an unverified or provisional factor is used
     */
    private String unverifiedFactorWarning;
}
