package com.carbonscope.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SourceInfoItem {
    private String id;
    private String name;
    private String organization;
    private String url;
    private String type;
    private String coverage;
    private String methodology;
    private String updateFrequency;
    private String reliabilityRating;
    private String roleInCarbonScope;
}
