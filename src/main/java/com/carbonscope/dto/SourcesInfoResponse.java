package com.carbonscope.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SourcesInfoResponse {
    private List<SourceInfoItem> sources;
    private String lastSynchronized;
    private String globalConsensusFramework;
}
