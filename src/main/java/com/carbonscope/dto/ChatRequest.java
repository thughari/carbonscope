package com.carbonscope.dto;

import java.util.List;

public record ChatRequest(
    String question, 
    String mode, 
    String country, 
    String continent,
    Integer year,
    List<HistoryItem> history
) {
    public record HistoryItem(String role, String text) {}
}