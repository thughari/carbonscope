package com.carbonscope.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrendPointDto {
    private int year;
    private double emissionsMt;
    private double worldEmissionsMt;
    private double shareOfGlobal;
}
