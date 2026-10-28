package com.carbonscope.dto.simulator;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Basic generation and dispatch constraints for a scenario.
 * Allows enforcing minimum residual generation (e.g. for baseload stability, thermal ramping,
 * or industrial co-generation) and maximum clean expansion limits.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScenarioConstraintsDto {
    /**
     * Minimum residual Coal generation that must be retained (TWh).
     * If specified, Coal generation cannot be reduced below this threshold.
     */
    private Double minResidualCoalTwh;

    /**
     * Minimum residual Fossil Gas generation that must be retained (TWh).
     * If specified, Gas generation cannot be reduced below this threshold.
     */
    private Double minResidualGasTwh;

    /**
     * Minimum residual Oil generation that must be retained (TWh).
     */
    private Double minResidualOilTwh;

    /**
     * Aggregate minimum fossil generation floor (TWh).
     */
    private Double minResidualFossilTwh;

    /**
     * Maximum clean generation addition limit across all interventions combined (TWh).
     * Represents grid integration capacity or supply chain addition limits.
     */
    private Double maxCleanAdditionLimitTwh;
}
