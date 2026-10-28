package com.carbonscope.dto.simulator;

import com.carbonscope.dto.ProvenanceDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecarbonizationScenarioResponse {
    private String countryCode;
    private String countryName;
    private String interventionName;
    private String accountingBoundary;
    private String boundaryDescription;
    private DecarbonizationBaselineDto baseline;
    private DecarbonizationResultsDto scenario;
    private DecarbonizationImpactDto impact;
    private ScenarioAssumptionsDto assumptions;
    private String calculationMethod;
    private String formula;
    private String accountingStandard;
    private List<String> limitations;
    private ProvenanceDto provenance;
    private String timestamp;
}
