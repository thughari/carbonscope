package com.carbonscope.dto.policy;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyComparisonResponse {
    private List<InterventionEvidenceDto> interventions;
    private CountryNdcProfileDto countryNdc;
    private ScenarioNdcComparisonDto scenarioComparison;
    private String methodologyNote;
    private String timestamp;
}
