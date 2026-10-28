package com.carbonscope.service;

import com.carbonscope.dto.ProvenanceDto;
import com.carbonscope.dto.policy.*;
import com.carbonscope.dto.simulator.CountryElectricityProfileDto;
import com.carbonscope.dto.simulator.DecarbonizationScenarioResponse;
import com.carbonscope.exception.SimulatorValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PolicyAnalysisService {

    private final DecarbonizationSimulatorService simulatorService;

    private static final List<InterventionEvidenceDto> EVIDENCE_INTERVENTIONS = new ArrayList<>();
    private static final Map<String, CountryNdcProfileDto> NDC_REGISTRY = new LinkedHashMap<>();

    static {
        initInterventionEvidence();
        initNdcRegistry();
    }

    public List<InterventionEvidenceDto> getAllInterventions() {
        return new ArrayList<>(EVIDENCE_INTERVENTIONS);
    }

    public InterventionEvidenceDto getInterventionById(String techId) {
        if (techId == null || techId.isBlank()) {
            throw new SimulatorValidationException("Technology identifier is required.");
        }
        return EVIDENCE_INTERVENTIONS.stream()
                .filter(i -> i.getTechnologyId().equalsIgnoreCase(techId.trim()))
                .findFirst()
                .orElseThrow(() -> new SimulatorValidationException("Unsupported technology identifier: " + techId));
    }

    public List<CountryNdcProfileDto> getAllNdcProfiles() {
        return new ArrayList<>(NDC_REGISTRY.values());
    }

    public CountryNdcProfileDto getNdcProfile(String countryCode) {
        if (countryCode == null || countryCode.isBlank()) {
            throw new SimulatorValidationException("Country code is required.");
        }
        CountryNdcProfileDto profile = NDC_REGISTRY.get(countryCode.trim().toUpperCase());
        if (profile == null) {
            throw new SimulatorValidationException("No verified UNFCCC NDC profile registered for country code: " + countryCode);
        }
        return profile;
    }

    public PolicyComparisonResponse getPolicyComparison(String countryCode, DecarbonizationScenarioResponse scenario) {
        CountryNdcProfileDto ndc = getNdcProfile(countryCode);
        ScenarioNdcComparisonDto scenarioComp = scenario != null ? compareScenarioWithNdc(scenario) : null;

        return PolicyComparisonResponse.builder()
                .interventions(getAllInterventions())
                .countryNdc(ndc)
                .scenarioComparison(scenarioComp)
                .methodologyNote("Intervention evidence compiled from IPCC AR5 WGIII Table A.III.2 (Schlömer et al., 2014) & 2006 Guidelines. NDC commitments sourced directly from official UNFCCC NDC Registry submissions.")
                .timestamp(Instant.now().toString())
                .build();
    }
    public ScenarioNdcComparisonDto compareScenarioWithNdc(DecarbonizationScenarioResponse scenario) {
        return compareScenarioWithNdc(scenario, null);
    }

    public ScenarioNdcComparisonDto compareScenarioWithNdc(DecarbonizationScenarioResponse scenario, String targetId) {
        if (scenario == null || scenario.getCountryCode() == null) {
            throw new SimulatorValidationException("Valid scenario response is required for NDC comparison.");
        }

        String code = scenario.getCountryCode().trim().toUpperCase();
        CountryNdcProfileDto ndc = getNdcProfile(code);
        CountryElectricityProfileDto baseline = simulatorService.getCountryProfile(code);

        double requestedClean = scenario.getScenario() != null
                ? scenario.getScenario().getTotalRequestedCleanGenerationTwh() : 0.0;
        double displacedFossil = scenario.getScenario() != null
                ? scenario.getScenario().getTotalDisplacedFossilGenerationTwh() : 0.0;
        double avoidedDirect = scenario.getImpact() != null ? scenario.getImpact().getAvoidedDirectEmissionsMt() : 0.0;
        double netAvoidedLca = scenario.getImpact() != null ? scenario.getImpact().getNetAvoidedLifecycleMt() : 0.0;
        double baselineCleanShare = baseline.getCleanPowerSharePct();
        double simulatedCleanShare = scenario.getScenario() != null ? scenario.getScenario().getSimulatedCleanPowerSharePct() : baselineCleanShare;
        double cleanShareDelta = scenario.getImpact() != null ? scenario.getImpact().getCleanShareDeltaPct() : 0.0;

        boolean isWithdrawnNonParty = "WITHDRAWN_NON_PARTY".equals(ndc.getTreatyParticipationStatus());
        String withdrawalDate = ndc.getTreatyWithdrawalEffectiveDate() != null
                ? ndc.getTreatyWithdrawalEffectiveDate() : "January 27, 2026";

        // Determine target to evaluate
        NdcTargetDto target;
        if (targetId != null && !targetId.isBlank()) {
            target = ndc.getTargets().stream()
                    .filter(t -> t.getId().equalsIgnoreCase(targetId.trim()))
                    .findFirst()
                    .orElseThrow(() -> new SimulatorValidationException("No target registered with ID: " + targetId + " for country: " + code));
        } else {
            // Default selection:
            // Prefer power sector generation target (e.g. domestic policy or NDC), then capacity target, then first target
            target = ndc.getTargets().stream()
                    .filter(t -> "POWER_SECTOR".equalsIgnoreCase(t.getSectorScope()) ||
                            t.getTargetType().contains("RENEWABLE_SHARE"))
                    .findFirst()
                    .orElse(ndc.getTargets().isEmpty() ? null : ndc.getTargets().get(0));
        }

        List<String> caveats = new ArrayList<>();
        caveats.add("Modeled avoided emissions reflect an analytical marginal substitution scenario, NOT an official government emissions projection or NDC fulfillment guarantee.");
        caveats.add("Emissions Boundary Rule: Net lifecycle abatement (" + String.format("%.2f", netAvoidedLca) + " MtCO₂e) cannot be subtracted from territorial national inventories without violating accounting boundaries.");

        if (target == null) {
            return ScenarioNdcComparisonDto.builder()
                    .countryCode(code)
                    .countryName(baseline.getCountryName())
                    .scenarioName(scenario.getInterventionName())
                    .requestedCleanTwh(requestedClean)
                    .displacedFossilTwh(displacedFossil)
                    .avoidedDirectMt(avoidedDirect)
                    .netAvoidedLifecycleMt(netAvoidedLca)
                    .baselineCleanSharePct(baselineCleanShare)
                    .simulatedCleanSharePct(simulatedCleanShare)
                    .cleanShareDeltaPct(cleanShareDelta)
                    .hasPowerSectorTarget(false)
                    .comparabilityStatus("DATA_UNAVAILABLE")
                    .alignmentStatus("NO_TARGETS_REGISTERED")
                    .alignmentSummary("No verified targets registered for this profile.")
                    .scopeMismatchExplanation("NDC profile contains no verified target entries.")
                    .methodologicalCaveats(caveats)
                    .build();
        }

        String comparedTitle = target.getTargetTitle();
        String comparedClassification = target.getTargetClassification();
        String comparedCurrency = target.getTargetCurrencyStatus();
        boolean hasPowerTarget = "POWER_SECTOR".equalsIgnoreCase(target.getSectorScope()) ||
                target.getTargetType().contains("RENEWABLE_SHARE");

        // GUARD: If country is a Non-Party AND target is an NDC treaty commitment (or historical superseded/withdrawn NDC):
        // Paris Agreement NDC compliance tracking is disabled for non-Parties!
        boolean isTreatyCommitment = "OFFICIAL_NDC_COMMITMENT".equals(target.getTargetClassification()) ||
                "JOINT_EU_NDC_COMMITMENT".equals(target.getTargetClassification()) ||
                "HISTORICAL_SUPERSEDED_OR_WITHDRAWN".equals(target.getTargetCurrencyStatus());

        if (isWithdrawnNonParty && isTreatyCommitment) {
            caveats.add("Treaty Non-Party Status: " + ndc.getCountryName() + " withdrawal from the Paris Agreement took effect on " + withdrawalDate + " pursuant to Article 28.");
            caveats.add("Historical Target Status: Deposited 2035 and 2030 NDC targets are retained strictly as historical archival records. They carry no active legal treaty force.");
            caveats.add("No Legal Compliance Benchmark: Avoided emissions in the power sector cannot be credited toward an active Paris Agreement NDC commitment.");

            return ScenarioNdcComparisonDto.builder()
                    .countryCode(code)
                    .countryName(baseline.getCountryName())
                    .scenarioName(scenario.getInterventionName())
                    .requestedCleanTwh(requestedClean)
                    .displacedFossilTwh(displacedFossil)
                    .avoidedDirectMt(avoidedDirect)
                    .netAvoidedLifecycleMt(netAvoidedLca)
                    .baselineCleanSharePct(baselineCleanShare)
                    .simulatedCleanSharePct(simulatedCleanShare)
                    .cleanShareDeltaPct(cleanShareDelta)
                    .hasPowerSectorTarget(false)
                    .comparedTargetTitle(comparedTitle)
                    .comparedTargetClassification(comparedClassification)
                    .comparedTargetCurrencyStatus(comparedCurrency)
                    .targetPowerCleanGenerationSharePct(null)
                    .targetPowerCapacitySharePct(null)
                    .cleanShareDistanceToTargetPct(null)
                    .comparabilityStatus("NON_PARTY_TREATY_WITHDRAWN")
                    .alignmentStatus("WITHDRAWN_TREATY_NON_PARTY")
                    .alignmentSummary(String.format("The %s formally withdrew from the Paris Agreement effective %s. Historical NDC targets are preserved for analytical tracking but carry no active legal treaty commitment.",
                            ndc.getCountryName(), withdrawalDate))
                    .scopeMismatchExplanation(String.format("The %s is a Non-Party to the Paris Agreement following the entry into force of its withdrawal on %s under Article 28. Preserved NDC targets are retained strictly as historical submission records. Modeled scenario fuel displacements are not compared against inactive treaty pledges.",
                            ndc.getCountryName(), withdrawalDate))
                    .methodologicalCaveats(caveats)
                    .build();
        }

        // EVALUATE COMPARISON: Metric & Scope Handling
        Double targetCleanGenerationShare = null;
        Double targetCapacityShare = null;
        Double distanceToTarget = null;
        String alignmentStatus;
        String comparabilityStatus;
        String alignmentSummary;
        String mismatchExplanation;

        if ("RENEWABLE_SHARE_CAPACITY".equals(target.getTargetType())) {
            targetCapacityShare = target.getTargetValue();
            targetCleanGenerationShare = null;
            distanceToTarget = null;
            alignmentStatus = "METRIC_MISMATCH_CAPACITY_VS_GENERATION";
            comparabilityStatus = "NON_COMPARABLE_CAPACITY_VS_GENERATION";
            alignmentSummary = String.format("Target is %.1f%% non-fossil INSTALLED CAPACITY (GW) by %d [%s: %s], whereas scenario models GENERATION (TWh, simulated %.1f%% clean power).",
                    targetCapacityShare != null ? targetCapacityShare : 0.0, target.getTargetYear(), target.getTargetClassification(), target.getTargetCurrencyStatus(), simulatedCleanShare);
            mismatchExplanation = "Capacity (nameplate GW) differs substantially from actual electrical generation (TWh) due to lower capacity factors of intermittent solar and wind (typically 18–32%) relative to thermal generators (55–75%). Achieving 50% or 60% capacity does not equate to equivalent generation. Direct distance calculation is suppressed to avoid misleading comparisons.";
            caveats.add("Capacity vs. Generation Disparity: Non-fossil capacity targets correspond to significantly lower generation shares without extensive energy storage and overbuilding.");
        } else if ("RENEWABLE_SHARE_GENERATION".equals(target.getTargetType())) {
            targetCleanGenerationShare = target.getTargetValue();
            if (targetCleanGenerationShare != null) {
                distanceToTarget = targetCleanGenerationShare - simulatedCleanShare;
            }
            alignmentStatus = "GENERATION_METRIC_COMPARABLE";
            comparabilityStatus = "GENERATION_METRIC_COMPARABLE";

            boolean isDomestic = "DOMESTIC_POLICY_LAW".equals(target.getTargetClassification()) ||
                    "OTHER_OFFICIAL_SOURCE".equals(target.getTargetClassification());

            if (isWithdrawnNonParty && isDomestic) {
                alignmentSummary = String.format("Simulated clean generation share reaches %.1f%% (baseline %.1f%%, target %.1f%% by %d under DOMESTIC_POLICY_LAW: %s). Note: Evaluated against domestic policy goal; %s is a Non-Party to the Paris Agreement.",
                        simulatedCleanShare, baselineCleanShare, targetCleanGenerationShare != null ? targetCleanGenerationShare : 0.0,
                        target.getTargetYear(), target.getLegalBasisCitation(), ndc.getCountryName());
                mismatchExplanation = String.format("Target and scenario both evaluate electrical generation shares (TWh basis). Classification: DOMESTIC_POLICY_LAW (%s). This evaluates a domestic national policy benchmark under sovereign authority. It is NOT an active UNFCCC Paris Agreement treaty commitment (%s is a Non-Party following withdrawal on %s).",
                        target.getLegalBasisCitation(), ndc.getCountryName(), withdrawalDate);
                caveats.add("Domestic Policy Authority: This comparison evaluates a domestic executive/statutory policy target (" + target.getLegalBasisCitation() + "), NOT an active UNFCCC Paris Agreement treaty commitment.");
                caveats.add("Treaty Non-Party Notice: " + ndc.getCountryName() + " formally withdrew from the Paris Agreement effective " + withdrawalDate + " pursuant to Article 28. Modeled scenario progress cannot be credited toward an active international treaty commitment.");
                caveats.add("Administrative Authority: Domestic executive orders and administrative regulations are established under national authority and remain distinct from international treaty commitments.");
            } else if (isDomestic) {
                alignmentSummary = String.format("Simulated clean generation share reaches %.1f%% (baseline %.1f%%, target %.1f%% by %d under %s).",
                        simulatedCleanShare, baselineCleanShare, targetCleanGenerationShare != null ? targetCleanGenerationShare : 0.0, target.getTargetYear(), target.getTargetClassification());
                mismatchExplanation = String.format("Target and scenario both evaluate electrical generation shares (TWh basis). Classification: %s (%s). This evaluates a national statutory target.",
                        target.getTargetClassification(), target.getLegalBasisCitation());
                caveats.add("Domestic Policy Target: Evaluated under national law (" + target.getLegalBasisCitation() + "); distinct from international NDC treaty filings.");
            } else {
                alignmentSummary = String.format("Simulated clean generation share reaches %.1f%% (baseline %.1f%%, target %.1f%% by %d under %s).",
                        simulatedCleanShare, baselineCleanShare, targetCleanGenerationShare != null ? targetCleanGenerationShare : 0.0, target.getTargetYear(), target.getTargetClassification());
                mismatchExplanation = String.format("Target and scenario both evaluate electrical generation shares (TWh basis). Classification: %s (%s).",
                        target.getTargetClassification(), target.getLegalBasisCitation());
            }
        } else if ("INTENSITY_GDP".equals(target.getTargetType())) {
            alignmentStatus = "SCOPE_MISMATCH_ECONOMY_WIDE_GDP";
            comparabilityStatus = "NON_COMPARABLE_ECONOMY_WIDE_GDP_INTENSITY";
            alignmentSummary = String.format("National commitment specifies an economy-wide emissions intensity reduction of %.1f%% per unit of GDP by %d below %d [%s: %s].",
                    target.getTargetValue(), target.getTargetYear(), target.getBaselineYear(),
                    target.getTargetClassification(), target.getTargetCurrencyStatus());
            mismatchExplanation = "The simulator models physical electricity sector fuel displacement (TWh), whereas this national target depends on economy-wide real GDP growth and non-power sectors (transport, industry, agriculture). Direct achievement percentages are withheld to prevent false equivalence.";
            caveats.add("GDP Intensity Dependence: High GDP growth reduces emissions intensity even with stable or rising absolute power sector emissions.");
        } else if ("ABSOLUTE_REDUCTION".equals(target.getTargetType())) {
            alignmentStatus = "DIFFERENT_SECTORAL_BOUNDARY";
            comparabilityStatus = "NON_COMPARABLE_ECONOMY_WIDE_NET_GHG";
            alignmentSummary = String.format("National commitment pledges an economy-wide net GHG reduction of %.1f%% by %d relative to %d [%s: %s].",
                    target.getTargetValue(), target.getTargetYear(), target.getBaselineYear(),
                    target.getTargetClassification(), target.getTargetCurrencyStatus());
            mismatchExplanation = "The simulator isolates the power generation sector, whereas the national commitment covers all economic sectors, international trade adjustments, and LULUCF land sinks. Modeled power sector avoided emissions represent only one sectoral contribution.";
            caveats.add("Sectoral Scope: Power sector decarbonization is critical but cannot guarantee achievement of economy-wide targets without parallel abatement in transport, heating, and industry.");
        } else {
            alignmentStatus = "POLICY_COMMITMENT_DOCUMENTED";
            comparabilityStatus = "NON_COMPARABLE_CUSTOM_BOUNDARY";
            alignmentSummary = target.getOfficialDescription() != null ? target.getOfficialDescription() : "Documented target";
            mismatchExplanation = "Target structure differs from single-sector electrical dispatch.";
        }

        return ScenarioNdcComparisonDto.builder()
                .countryCode(code)
                .countryName(baseline.getCountryName())
                .scenarioName(scenario.getInterventionName())
                .requestedCleanTwh(requestedClean)
                .displacedFossilTwh(displacedFossil)
                .avoidedDirectMt(avoidedDirect)
                .netAvoidedLifecycleMt(netAvoidedLca)
                .baselineCleanSharePct(baselineCleanShare)
                .simulatedCleanSharePct(simulatedCleanShare)
                .cleanShareDeltaPct(cleanShareDelta)
                .hasPowerSectorTarget(hasPowerTarget)
                .comparedTargetTitle(comparedTitle)
                .comparedTargetClassification(comparedClassification)
                .comparedTargetCurrencyStatus(comparedCurrency)
                .targetPowerCleanGenerationSharePct(targetCleanGenerationShare)
                .targetPowerCapacitySharePct(targetCapacityShare)
                .cleanShareDistanceToTargetPct(distanceToTarget)
                .comparabilityStatus(comparabilityStatus)
                .alignmentStatus(alignmentStatus)
                .alignmentSummary(alignmentSummary)
                .scopeMismatchExplanation(mismatchExplanation)
                .methodologicalCaveats(caveats)
                .build();
    }

    private static void initInterventionEvidence() {
        // 1. Utility-scale Solar PV (Crystalline Silicon)
        // IPCC AR5 WGIII Annex III Table A.III.2 (Schlömer et al., 2014) reports Utility-scale Solar PV as 18 / 48 / 180 gCO2eq/kWh.
        // (Note: 26 / 41 / 60 belongs to Rooftop Solar PV in Table A.III.2).
        EVIDENCE_INTERVENTIONS.add(InterventionEvidenceDto.builder()
                .technologyId("SOLAR_PV")
                .displayName("Utility-Scale Solar PV (Crystalline Silicon)")
                .category("CLEAN_GENERATION")
                .directFactorGco2PerKwh(0.0)
                .lifecycleFactorMedianGco2PerKwh(48.0)
                .lifecycleFactorMinGco2PerKwh(18.0)
                .lifecycleFactorMaxGco2PerKwh(180.0)
                .emissionFactorUnit("gCO2e/kWh")
                .sourceCitation("IPCC AR5 WGIII Annex III Table A.III.2 (Schlömer et al., 2014, Utility-scale)")
                .publicationYear(2014)
                .accountingBoundary("CRADLE_TO_GRAVE_SCOPE_1_2_3")
                .technologyScope("Polycrystalline and monocrystalline silicon ground-mounted utility arrays (18–180 gCO2e/kWh; 50th percentile median 48 gCO2e/kWh). Excludes rooftop PV (26–60 gCO2e/kWh).")
                .geographicApplicability("Global harmonized literature (insolation 1,700–2,200 kWh/m²/yr, 30-year lifetime, 80% performance ratio).")
                .dataQualityRating("Gold Standard Peer-Reviewed (Harmonized meta-analysis of 100+ utility-scale lifecycle studies).")
                .isProvisional(false)
                .physicalLimitations(List.of(
                        "Diurnal Intermittency: Generation is strictly confined to daylight hours; cannot displace nighttime baseload without battery/pumped hydro storage.",
                        "Seasonal Insolation Variance: Output varies significantly between summer and winter at mid-to-high latitudes.",
                        "Land Footprint: Requires approximately 2–3 hectares per MW installed capacity for utility-scale ground mount."
                ))
                .lcoeDataAvailable(false)
                .lcoeUnavailableReason("Empirical LCOE ($/MWh) varies by local auction tenders, capital financing rates (WACC), and grid connection costs; not harmonized in IPCC Table A.III.2. Marked UNAVAILABLE to prevent fabrication.")
                .capacityFactorDataAvailable(false)
                .capacityFactorUnavailableReason("Empirical capacity factors (typically 18–28%) depend on local solar irradiance and tracking hardware; not published as a universal constant in IPCC Table A.III.2.")
                .build());

        // 2. Onshore Wind Power
        // IPCC AR5 WGIII Annex III Table A.III.2 reports Onshore Wind as 7.0 / 11.0 / 56.0 gCO2eq/kWh.
        EVIDENCE_INTERVENTIONS.add(InterventionEvidenceDto.builder()
                .technologyId("WIND_ONSHORE")
                .displayName("Onshore Wind Power")
                .category("CLEAN_GENERATION")
                .directFactorGco2PerKwh(0.0)
                .lifecycleFactorMedianGco2PerKwh(11.0)
                .lifecycleFactorMinGco2PerKwh(7.0)
                .lifecycleFactorMaxGco2PerKwh(56.0)
                .emissionFactorUnit("gCO2e/kWh")
                .sourceCitation("IPCC AR5 WGIII Annex III Table A.III.2 (Schlömer et al., 2014)")
                .publicationYear(2014)
                .accountingBoundary("CRADLE_TO_GRAVE_SCOPE_1_2_3")
                .technologyScope("Modern multi-megawatt horizontal-axis turbines, concrete foundations, steel towers, fiberglass blades, transformer sub-stations, and end-of-life recycling.")
                .geographicApplicability("Global harmonized literature (20–25 year design lifetime, hub heights 80–120m).")
                .dataQualityRating("Gold Standard Peer-Reviewed (Extensive industry and academic lifecycle harmonization).")
                .isProvisional(false)
                .physicalLimitations(List.of(
                        "Wind Resource Variability: Generation depends on cubic wind speed ($v^3$); output fluctuates across multi-day weather patterns.",
                        "Transmission Bottlenecks: Prime wind corridors are often geographically remote from major industrial demand centers.",
                        "Acoustic & Environmental Constraints: Requires setback distances from residential communities and avian migration flyways."
                ))
                .lcoeDataAvailable(false)
                .lcoeUnavailableReason("Commercial turbine supply contracts, regional civil works, and grid interconnection costs vary geographically; not harmonized in Table A.III.2.")
                .capacityFactorDataAvailable(false)
                .capacityFactorUnavailableReason("Capacity factors vary widely by local Weibull wind distribution (typically 25–45%).")
                .build());

        // 3. Offshore Wind Power
        // IPCC AR5 WGIII Annex III Table A.III.2 reports Offshore Wind as 8.0 / 12.0 / 35.0 gCO2eq/kWh.
        EVIDENCE_INTERVENTIONS.add(InterventionEvidenceDto.builder()
                .technologyId("WIND_OFFSHORE")
                .displayName("Offshore Wind Power")
                .category("CLEAN_GENERATION")
                .directFactorGco2PerKwh(0.0)
                .lifecycleFactorMedianGco2PerKwh(12.0)
                .lifecycleFactorMinGco2PerKwh(8.0)
                .lifecycleFactorMaxGco2PerKwh(35.0)
                .emissionFactorUnit("gCO2e/kWh")
                .sourceCitation("IPCC AR5 WGIII Annex III Table A.III.2 (Schlömer et al., 2014)")
                .publicationYear(2014)
                .accountingBoundary("CRADLE_TO_GRAVE_SCOPE_1_2_3")
                .technologyScope("Fixed-bottom monopiles and jacket foundations, specialized offshore installation vessels, subsea high-voltage export cables, and offshore substations.")
                .geographicApplicability("Coastal shallow and intermediate water shelves (North Sea, Baltic Sea, East China Sea, US Atlantic coast).")
                .dataQualityRating("Gold Standard Peer-Reviewed (Comprehensive marine lifecycle literature).")
                .isProvisional(false)
                .physicalLimitations(List.of(
                        "Marine Logistics & Harsh Conditions: Higher operational expenditure (OPEX) and vessel availability constraints for offshore maintenance.",
                        "Subsea Transmission: Requires high-voltage AC or DC (HVDC) subsea cabling and offshore transformer platforms.",
                        "Depth Limitations: Fixed foundations are constrained to water depths < 60m; floating platforms are in early commercial deployment."
                ))
                .lcoeDataAvailable(false)
                .lcoeUnavailableReason("High sensitivity to seabed bathymetry, distance to port, and marine installation logistics.")
                .capacityFactorDataAvailable(false)
                .capacityFactorUnavailableReason("High capacity factors (typically 40–55%) require regional offshore meteorological mast data.")
                .build());

        // 4. Nuclear Power
        // IPCC AR5 WGIII Annex III Table A.III.2 reports Nuclear as 3.7 / 12.0 / 110.0 gCO2eq/kWh.
        EVIDENCE_INTERVENTIONS.add(InterventionEvidenceDto.builder()
                .technologyId("NUCLEAR")
                .displayName("Nuclear Power (Gen-III/III+ PWR & BWR)")
                .category("CLEAN_GENERATION")
                .directFactorGco2PerKwh(0.0)
                .lifecycleFactorMedianGco2PerKwh(12.0)
                .lifecycleFactorMinGco2PerKwh(3.7)
                .lifecycleFactorMaxGco2PerKwh(110.0)
                .emissionFactorUnit("gCO2e/kWh")
                .sourceCitation("IPCC AR5 WGIII Annex III Table A.III.2 (Schlömer et al., 2014)")
                .publicationYear(2014)
                .accountingBoundary("CRADLE_TO_GRAVE_SCOPE_1_2_3")
                .technologyScope("Uranium mining, milling, centrifuge enrichment, fuel fabrication, plant construction, 60-year operation, decommissioning, and spent fuel geological repository management.")
                .geographicApplicability("Global commercial light-water reactor fleet.")
                .dataQualityRating("Gold Standard Peer-Reviewed (Harmonized nuclear fuel cycle studies).")
                .isProvisional(false)
                .physicalLimitations(List.of(
                        "Construction Lead Times: Construction cycles typically span 8–15+ years with stringent licensing requirements.",
                        "Baseload Operating Rigidity: Large thermal reactors operate most economically as continuous baseload and have limited fast-ramping cycling capability.",
                        "Cooling Water Requirements: Requires substantial access to rivers, lakes, or ocean coastlines for condenser heat rejection."
                ))
                .lcoeDataAvailable(false)
                .lcoeUnavailableReason("Capital cost amortization, financing interest during construction (IDC), and regulatory costs are country-specific.")
                .capacityFactorDataAvailable(false)
                .capacityFactorUnavailableReason("High availability factors (typically 80–93%) depend on scheduled refueling outages.")
                .build());

        // 5. Hydropower
        // IPCC AR5 WGIII Annex III Table A.III.2 reports Hydropower as 1.0 / 24.0 / 2200.0 gCO2eq/kWh.
        EVIDENCE_INTERVENTIONS.add(InterventionEvidenceDto.builder()
                .technologyId("HYDRO")
                .displayName("Hydropower (Run-of-River & Reservoir Impoundment)")
                .category("CLEAN_GENERATION")
                .directFactorGco2PerKwh(0.0)
                .lifecycleFactorMedianGco2PerKwh(24.0)
                .lifecycleFactorMinGco2PerKwh(1.0)
                .lifecycleFactorMaxGco2PerKwh(2200.0)
                .emissionFactorUnit("gCO2e/kWh")
                .sourceCitation("IPCC AR5 WGIII Annex III Table A.III.2 (Schlömer et al., 2014)")
                .publicationYear(2014)
                .accountingBoundary("CRADLE_TO_GRAVE_SCOPE_1_2_3")
                .technologyScope("Civil dam engineering, massive concrete works, electrical turbines, and biogenic reservoir GHG emissions (methane and CO2 from flooded organic matter).")
                .geographicApplicability("Global installations; upper bound reflects shallow, warm tropical reservoirs with high organic inundation.")
                .dataQualityRating("High Variance Peer-Reviewed (Extreme spatial variation across temperate alpine run-of-river vs. tropical low-head reservoirs).")
                .isProvisional(false)
                .physicalLimitations(List.of(
                        "Hydrological Seasonality: Generation is governed by rainfall patterns, snowmelt, and vulnerability to multi-year drought cycles.",
                        "Geographic Topography Constraints: Dependent on natural river head and topographical canyon suitability.",
                        "Ecological & Social Disruption: Large reservoirs involve inundation of land, displacement of riverine ecology, and community relocation."
                ))
                .lcoeDataAvailable(false)
                .lcoeUnavailableReason("Site-specific civil engineering and geological tunneling costs dominate CAPEX.")
                .capacityFactorDataAvailable(false)
                .capacityFactorUnavailableReason("Capacity factors depend strictly on annual watershed precipitation (typically 30–60%).")
                .build());

        // 6. Pulverized Coal
        // Table A.III.2 reports Coal PC as 740 / 820 / 910 gCO2eq/kWh; IPCC 2006 reports direct combustion as 950 gCO2/kWh @ 36% efficiency.
        EVIDENCE_INTERVENTIONS.add(InterventionEvidenceDto.builder()
                .technologyId("COAL")
                .displayName("Pulverized Coal (Supercritical & Subcritical Fleet)")
                .category("FOSSIL_DISPLACEMENT")
                .directFactorGco2PerKwh(950.0)
                .lifecycleFactorMedianGco2PerKwh(820.0)
                .lifecycleFactorMinGco2PerKwh(740.0)
                .lifecycleFactorMaxGco2PerKwh(910.0)
                .emissionFactorUnit("gCO2e/kWh (Lifecycle) / gCO2/kWh (Direct)")
                .sourceCitation("IPCC AR5 WGIII Table A.III.2 & IPCC 2006 Stationary Combustion Table 2.2")
                .publicationYear(2014)
                .accountingBoundary("DUAL_REPORTED_SCOPE_1_AND_LIFECYCLE")
                .technologyScope("Underground and open-pit coal extraction, coal washing, rail/marine transport, pulverization, boiler combustion, flue gas desulfurization, and ash management.")
                .geographicApplicability("Global pulverized coal fleet; direct combustion assumes 95,000 kg/TJ NCV with 36.0% fleet average thermal efficiency.")
                .dataQualityRating("Authoritative Benchmark (IPCC 2006 guidelines and AR5 Table A.III.2).")
                .isProvisional(false)
                .physicalLimitations(List.of(
                        "Thermal Inertia & Minimum Loading: Minimum technical stable generation floor typically 40–55% of rated capacity; cold start times 6–12 hours.",
                        "Air Pollutant Co-Emissions: Major point source of SO2, NOx, mercury, and particulate matter (PM2.5).",
                        "High Fuel Supply Chain Reliance: Requires continuous rail or port freight logistics for solid bulk fuel delivery."
                ))
                .lcoeDataAvailable(false)
                .lcoeUnavailableReason("Fuel coal price indices (e.g. Newcastle, API2) and domestic subsidies vary significantly.")
                .capacityFactorDataAvailable(false)
                .capacityFactorUnavailableReason("Historically operated as baseload (60–80%), now flexing to lower capacity factors as renewables expand.")
                .build());

        // 7. Fossil Gas (Combined Cycle Gas Turbine)
        // Table A.III.2 reports CCGT as 410 / 490 / 650 gCO2eq/kWh; IPCC 2006 reports direct combustion as 410 gCO2/kWh @ 49.25% efficiency.
        EVIDENCE_INTERVENTIONS.add(InterventionEvidenceDto.builder()
                .technologyId("FOSSIL_GAS")
                .displayName("Fossil Gas (Combined Cycle Gas Turbine - CCGT)")
                .category("FOSSIL_DISPLACEMENT")
                .directFactorGco2PerKwh(410.0)
                .lifecycleFactorMedianGco2PerKwh(490.0)
                .lifecycleFactorMinGco2PerKwh(410.0)
                .lifecycleFactorMaxGco2PerKwh(650.0)
                .emissionFactorUnit("gCO2e/kWh (Lifecycle) / gCO2/kWh (Direct)")
                .sourceCitation("IPCC AR5 WGIII Table A.III.2 & IPCC 2006 Stationary Combustion Table 2.2")
                .publicationYear(2014)
                .accountingBoundary("DUAL_REPORTED_SCOPE_1_AND_LIFECYCLE")
                .technologyScope("Conventional and shale gas extraction, gas sweetening, pipeline transmission, LNG liquefaction/shipping/regasification, CCGT power generation, and upstream fugitive methane leaks.")
                .geographicApplicability("Modern CCGT power generation (49.25% thermal efficiency default, 56,100 kg/TJ NCV direct combustion).")
                .dataQualityRating("Authoritative Benchmark (IPCC Table A.III.2; upper bound captures high fugitive methane leakage rates).")
                .isProvisional(false)
                .physicalLimitations(List.of(
                        "Fugitive Methane Sensitivity: Upstream venting, flaring, and transmission pipeline leaks of CH4 (GWP 27.9–29.8 over 100yr) substantially impact total lifecycle GHG intensity.",
                        "Gas Pipeline Reliance: Dependent on pressurized pipeline networks or LNG regasification terminal infrastructure.",
                        "Fuel Price Volatility: Exposure to international wholesale Henry Hub, TTF, and JKM spot market price swings."
                ))
                .lcoeDataAvailable(false)
                .lcoeUnavailableReason("Extremely sensitive to international natural gas and LNG spot prices.")
                .capacityFactorDataAvailable(false)
                .capacityFactorUnavailableReason("Dispatched dynamically across intermediate and peaking load (30–65%).")
                .build());

        // 8. Oil / Diesel Peakers
        // Note: Table A.III.2 has NO dedicated entry for oil. Explicitly kept provisional (500–800 gCO2e/kWh, median 750).
        EVIDENCE_INTERVENTIONS.add(InterventionEvidenceDto.builder()
                .technologyId("OIL")
                .displayName("Oil / Diesel Peakers & Liquid Fuel Plants")
                .category("FOSSIL_DISPLACEMENT")
                .directFactorGco2PerKwh(720.0)
                .lifecycleFactorMedianGco2PerKwh(750.0)
                .lifecycleFactorMinGco2PerKwh(500.0)
                .lifecycleFactorMaxGco2PerKwh(800.0)
                .emissionFactorUnit("gCO2e/kWh (Lifecycle) / gCO2/kWh (Direct)")
                .sourceCitation("Provisional Peaker Estimate & IPCC 2006 Stationary Combustion Table 2.2")
                .publicationYear(2014)
                .accountingBoundary("PROVISIONAL_ESTIMATE")
                .technologyScope("Crude oil refining, residual fuel oil/diesel transport, and open-cycle peaking generation. Note: Table A.III.2 does not publish a dedicated oil lifecycle entry.")
                .geographicApplicability("Emergency and fast-start open-cycle peaking units, island grids, and remote generator sets.")
                .dataQualityRating("PROVISIONAL / UNVERIFIED IN TABLE A.III.2")
                .isProvisional(true)
                .provisionalWarning("[PROVISIONAL / UNVERIFIED IN TABLE A.III.2] Dedicated oil-fired power lifecycle factor is not published in IPCC AR5 Table A.III.2; provisional peaker estimate (500–800 gCO2e/kWh) applied.")
                .physicalLimitations(List.of(
                        "Provisional Life-Cycle Metric: Lifecycle factor is derived from peaker estimates and has higher literature uncertainty than verified coal or gas entries.",
                        "High Fuel Operating Costs: Highly expensive operational fuel costs, generally reserved exclusively for emergency grid peaking and reserve margins.",
                        "Air Quality Emissions: Significant local emissions of sulfur oxides (SOx), nitrogen oxides (NOx), and diesel particulate matter."
                ))
                .lcoeDataAvailable(false)
                .lcoeUnavailableReason("Typically highest LCOE in grid fleets due to low utilization and high liquid fuel costs.")
                .capacityFactorDataAvailable(false)
                .capacityFactorUnavailableReason("Low capacity factor peaking operation (typically 5–15%).")
                .build());
    }

    private static void initNdcRegistry() {
        ProvenanceDto unfcccProv = ProvenanceDto.builder()
                .status("VERIFIED_SOURCE_OBSERVATION")
                .sourceName("UNFCCC NDC Interim Registry (Official Submissions under Paris Agreement Article 4)")
                .methodology("Direct Party submissions to the UNFCCC NDC Registry (unfccc.int/NDCREG), verified as of October 2026.")
                .empirical(true)
                .temporalCompleteness("OFFICIAL_NATIONAL_COMMITMENT")
                .isPartialOrProvisional(false)
                .qualityNotes("Documented directly from official national submissions and national climate laws. Classifications distinguish official NDCs from domestic statutory acts.")
                .build();

        // 1. India (IND)
        // Latest Active: NDC 3.0 (2031-2035) submitted 2026-04-24.
        // Historical: Updated First NDC (2021-2030) submitted 2022-08-26.
        NDC_REGISTRY.put("IND", CountryNdcProfileDto.builder()
                .countryCode("IND")
                .countryName("India")
                .documentTitle("India's Third Nationally Determined Contribution (NDC 3.0: 2031-2035) & Updated First NDC")
                .submissionDate("2026-04-24")
                .latestSubmissionDate("2026-04-24")
                .activeNdcCycle("NDC 3.0 (2031–2035 Horizon)")
                .previousSubmissionsSummary("Updated First NDC submitted 2022-08-26 (pledged 45% GDP intensity and 50% non-fossil capacity by 2030; crossed 52% non-fossil capacity ahead of schedule in early 2026).")
                .unfcccRegistryUrl("https://unfccc.int/NDCREG")
                .documentLanguage("English")
                .longTermGoal("Net-zero greenhouse gas emissions by 2070 and national vision of Viksit Bharat @2047.")
                .targetYearPrimary(2035)
                .policyReviewStatus("VERIFIED_ACTIVE")
                .treatyParticipationStatus("PARTY_ACTIVE")
                .verificationDate("2026-10-28")
                .targets(List.of(
                        // Active Latest 2035 Targets (NDC 3.0)
                        NdcTargetDto.builder()
                                .id("IND-2035-T1")
                                .targetTitle("Emissions Intensity Reduction of GDP (2035 Target)")
                                .targetType("INTENSITY_GDP")
                                .targetClassification("OFFICIAL_NDC_COMMITMENT")
                                .targetCurrencyStatus("ACTIVE_LATEST_COMMITMENT")
                                .targetYear(2035)
                                .baselineYear(2005)
                                .commitmentStatus("UNCONDITIONAL")
                                .targetValue(47.0)
                                .targetUnit("PERCENT")
                                .sectorScope("ECONOMY_WIDE")
                                .gasesCovered(List.of("CO2", "CH4", "N2O", "HFCs", "PFCs", "SF6"))
                                .officialDescription("Reduce emissions intensity of GDP by 47 percent by 2035 from 2005 levels under India's NDC 3.0 submission.")
                                .powerSectorImplication("Power sector fuel substitution accelerates national decoupling of GDP growth from carbon emissions.")
                                .interpretationCaveats(List.of("Macroeconomic Scope Mismatch: Intensity target indexed to GDP growth; not an absolute power emissions cap."))
                                .legalBasisCitation("UNFCCC NDC Registry (Submission Date: 2026-04-24)")
                                .verificationDate("2026-10-28")
                                .build(),
                        NdcTargetDto.builder()
                                .id("IND-2035-T2")
                                .targetTitle("Non-Fossil Electric Installed Capacity Share (2035 Target)")
                                .targetType("RENEWABLE_SHARE_CAPACITY")
                                .targetClassification("OFFICIAL_NDC_COMMITMENT")
                                .targetCurrencyStatus("ACTIVE_LATEST_COMMITMENT")
                                .targetYear(2035)
                                .baselineYear(null)
                                .commitmentStatus("CONDITIONAL")
                                .targetValue(60.0)
                                .targetUnit("PERCENT")
                                .sectorScope("POWER_SECTOR")
                                .gasesCovered(List.of("CO2"))
                                .officialDescription("Achieve 60 percent cumulative electric power installed capacity from non-fossil fuel-based energy resources by 2035.")
                                .powerSectorImplication("Targets nameplate installed capacity (GW) across solar, wind, hydro, nuclear, and biomass.")
                                .interpretationCaveats(List.of(
                                        "Capacity vs. Generation Disparity: 60% capacity (GW) does NOT equate to 60% generation (TWh) due to renewable capacity factor differences (20–30% RE vs 60–75% thermal).",
                                        "Conditional on international climate finance and concessional technology transfers."
                                ))
                                .legalBasisCitation("UNFCCC NDC Registry (Submission Date: 2026-04-24)")
                                .verificationDate("2026-10-28")
                                .build(),
                        NdcTargetDto.builder()
                                .id("IND-2035-T3")
                                .targetTitle("Carbon Sink Expansion via Forest Cover (2035 Target)")
                                .targetType("ABSOLUTE_REDUCTION")
                                .targetClassification("OFFICIAL_NDC_COMMITMENT")
                                .targetCurrencyStatus("ACTIVE_LATEST_COMMITMENT")
                                .targetYear(2035)
                                .baselineYear(null)
                                .commitmentStatus("UNCONDITIONAL")
                                .targetRange("3.5 to 4.0 Gt CO2e")
                                .targetUnit("GT_CO2E")
                                .sectorScope("LULUCF")
                                .gasesCovered(List.of("CO2"))
                                .officialDescription("Create an additional cumulative carbon sink of 3.5 to 4.0 billion tonnes of CO2e through enhanced forest and tree cover by 2035.")
                                .powerSectorImplication("Land use carbon sink outside the power sector boundary.")
                                .interpretationCaveats(List.of("LULUCF land sink accounting; excluded from electricity generation dispatch."))
                                .legalBasisCitation("UNFCCC NDC Registry (Submission Date: 2026-04-24)")
                                .verificationDate("2026-10-28")
                                .build(),
                        // Historical 2030 Milestones (Retained for Traceability)
                        NdcTargetDto.builder()
                                .id("IND-2030-T1")
                                .targetTitle("Emissions Intensity Reduction of GDP (2030 Milestone)")
                                .targetType("INTENSITY_GDP")
                                .targetClassification("OFFICIAL_NDC_COMMITMENT")
                                .targetCurrencyStatus("HISTORICAL_INTERMEDIATE_TARGET")
                                .targetYear(2030)
                                .baselineYear(2005)
                                .commitmentStatus("UNCONDITIONAL")
                                .targetValue(45.0)
                                .targetUnit("PERCENT")
                                .sectorScope("ECONOMY_WIDE")
                                .officialDescription("Reduce emissions intensity of GDP by 45 percent by 2030 from 2005 levels (Updated First NDC).")
                                .interpretationCaveats(List.of("Intermediate 2030 milestone; superseded by 2035 NDC 3.0."))
                                .legalBasisCitation("UNFCCC NDC Registry (Submission Date: 2022-08-26)")
                                .verificationDate("2026-10-28")
                                .build(),
                        NdcTargetDto.builder()
                                .id("IND-2030-T2")
                                .targetTitle("Non-Fossil Installed Capacity Share (2030 Milestone - Achieved Early)")
                                .targetType("RENEWABLE_SHARE_CAPACITY")
                                .targetClassification("OFFICIAL_NDC_COMMITMENT")
                                .targetCurrencyStatus("HISTORICAL_INTERMEDIATE_TARGET")
                                .targetYear(2030)
                                .baselineYear(null)
                                .commitmentStatus("CONDITIONAL")
                                .targetValue(50.0)
                                .targetUnit("PERCENT")
                                .sectorScope("POWER_SECTOR")
                                .officialDescription("Achieve 50 percent cumulative electric power installed capacity from non-fossil resources by 2030. Note: India surpassed this milestone in early 2026 (>52% non-fossil capacity).")
                                .interpretationCaveats(List.of("Milestone achieved ahead of schedule; superseded by 60% 2035 target."))
                                .legalBasisCitation("UNFCCC NDC Registry (Submission Date: 2022-08-26)")
                                .verificationDate("2026-10-28")
                                .build(),
                        // Domestic Policy Target
                        NdcTargetDto.builder()
                                .id("IND-DOM-NEP")
                                .targetTitle("National Electricity Plan Non-Fossil Capacity Target")
                                .targetType("RENEWABLE_SHARE_CAPACITY")
                                .targetClassification("DOMESTIC_POLICY_LAW")
                                .targetCurrencyStatus("STATUTORY_DOMESTIC")
                                .targetYear(2030)
                                .baselineYear(null)
                                .commitmentStatus("UNCONDITIONAL")
                                .targetValue(500.0)
                                .targetUnit("GW")
                                .sectorScope("POWER_SECTOR")
                                .officialDescription("500 GW of non-fossil electricity capacity by 2030 under Central Electricity Authority (CEA) National Electricity Plan.")
                                .powerSectorImplication("Domestic statutory roadmap guiding grid transmission buildout.")
                                .interpretationCaveats(List.of("Domestic policy roadmap; not an international treaty commitment in the UNFCCC registry."))
                                .legalBasisCitation("Ministry of Power / Central Electricity Authority (CEA) National Electricity Plan 2023")
                                .verificationDate("2026-10-28")
                                .build()
                ))
                .generalCaveats(List.of(
                        "India's NDC targets specify emissions intensity of GDP and installed capacity, not absolute power generation emissions caps.",
                        "Direct subtraction of power generation avoidance from national inventory is blocked to prevent false equivalence."
                ))
                .provenance(unfcccProv)
                .build());

        // 2. European Union Joint NDC & Member States (DEU, FRA, ITA, ESP, POL)
        // Latest Active: EU Updated NDC (2025 Submission) with 2030 (-55%), 2035 (66.25% to 72.5%), 2040 (-90%).
        registerEuMember("DEU", "Germany",
                "Federal Climate Action Act (Klimaschutzgesetz 2021) & Renewable Energy Sources Act (EEG 2023)",
                65.0, 80.0,
                "Climate neutrality by 2045; -88% net GHG by 2040.", unfcccProv);

        registerEuMember("ESP", "Spain",
                "Law 7/2021 on Climate Change and Energy Transition & PNIEC 2023-2030",
                55.0, 81.0,
                "Climate neutrality by 2050; coal phase-out by 2025.", unfcccProv);

        registerEuMember("ITA", "Italy",
                "Piano Nazionale Integrato per l'Energia e il Clima (PNIEC 2024)",
                55.0, 65.0,
                "National target of 65% renewable electricity generation by 2030; coal phase-out by 2025.", unfcccProv);

        registerEuMember("FRA", "France",
                "Stratégie Nationale Bas-Carbone (SNBC-2) & Energy-Climate Law",
                55.0, null,
                "Carbon neutrality by 2050; power sector already >90% decarbonized (nuclear + hydro).", unfcccProv);

        registerEuMember("POL", "Poland",
                "Energy Policy of Poland until 2040 (PEP2040)",
                55.0, 32.0,
                "Reduce coal power generation share to <= 56% by 2030; deploy Baltic offshore wind and nuclear.", unfcccProv);

        // 3. United Kingdom (GBR)
        // Latest Active: 2035 NDC submitted 2025-01-30 (-81% by 2035 vs 1990).
        // Historical: Updated NDC 2022 (-68% by 2030 vs 1990).
        NDC_REGISTRY.put("GBR", CountryNdcProfileDto.builder()
                .countryCode("GBR")
                .countryName("United Kingdom")
                .documentTitle("The UK's 2035 Nationally Determined Contribution & Updated First NDC")
                .submissionDate("2025-01-30")
                .latestSubmissionDate("2025-01-30")
                .activeNdcCycle("NDC 3.0 (2035 Horizon)")
                .previousSubmissionsSummary("Updated First NDC submitted 2022-09-23 (-68% by 2030 vs 1990; coal power generation fully phased out in 2024).")
                .unfcccRegistryUrl("https://unfccc.int/NDCREG")
                .documentLanguage("English")
                .longTermGoal("Net zero greenhouse gas emissions by 2050 (legally binding under Climate Change Act).")
                .targetYearPrimary(2035)
                .policyReviewStatus("VERIFIED_ACTIVE")
                .treatyParticipationStatus("PARTY_ACTIVE")
                .verificationDate("2026-10-28")
                .targets(List.of(
                        // Active Latest 2035 Target
                        NdcTargetDto.builder()
                                .id("GBR-2035-T1")
                                .targetTitle("Territorial Net GHG Reduction (2035 Target)")
                                .targetType("ABSOLUTE_REDUCTION")
                                .targetClassification("OFFICIAL_NDC_COMMITMENT")
                                .targetCurrencyStatus("ACTIVE_LATEST_COMMITMENT")
                                .targetYear(2035)
                                .baselineYear(1990)
                                .commitmentStatus("UNCONDITIONAL")
                                .targetValue(81.0)
                                .targetUnit("PERCENT")
                                .sectorScope("ECONOMY_WIDE")
                                .gasesCovered(List.of("CO2", "CH4", "N2O", "HFCs", "PFCs", "SF6", "NF3"))
                                .officialDescription("Reduce economy-wide territorial greenhouse gas emissions by at least 81 percent by 2035 compared to 1990 levels.")
                                .powerSectorImplication("Enforces deep decarbonization of remaining natural gas generation via renewables, nuclear, and CCUS.")
                                .interpretationCaveats(List.of("Baseline year is 1990. Excludes international aviation and shipping from headline target boundary."))
                                .legalBasisCitation("UNFCCC NDC Registry (Submission Date: 2025-01-30)")
                                .verificationDate("2026-10-28")
                                .build(),
                        // Historical 2030 Milestone
                        NdcTargetDto.builder()
                                .id("GBR-2030-T1")
                                .targetTitle("Economy-wide GHG Reduction (2030 Milestone)")
                                .targetType("ABSOLUTE_REDUCTION")
                                .targetClassification("OFFICIAL_NDC_COMMITMENT")
                                .targetCurrencyStatus("HISTORICAL_INTERMEDIATE_TARGET")
                                .targetYear(2030)
                                .baselineYear(1990)
                                .commitmentStatus("UNCONDITIONAL")
                                .targetValue(68.0)
                                .targetUnit("PERCENT")
                                .sectorScope("ECONOMY_WIDE")
                                .officialDescription("Reduce economy-wide emissions by at least 68 percent by 2030 below 1990 levels.")
                                .interpretationCaveats(List.of("Intermediate 2030 milestone; reaffirmed in 2035 submission."))
                                .legalBasisCitation("UNFCCC NDC Registry (Submission Date: 2022-09-23)")
                                .verificationDate("2026-10-28")
                                .build(),
                        // Domestic Policy Target
                        NdcTargetDto.builder()
                                .id("GBR-DOM-CLEAN")
                                .targetTitle("Clean Power 2030 Mission / Decarbonized Electricity")
                                .targetType("RENEWABLE_SHARE_GENERATION")
                                .targetClassification("DOMESTIC_POLICY_LAW")
                                .targetCurrencyStatus("STATUTORY_DOMESTIC")
                                .targetYear(2030)
                                .baselineYear(null)
                                .commitmentStatus("UNCONDITIONAL")
                                .targetValue(100.0)
                                .targetUnit("PERCENT")
                                .sectorScope("POWER_SECTOR")
                                .officialDescription("Deliver clean power by 2030 across Great Britain (NESO Clean Power 2030 Advice).")
                                .powerSectorImplication("Domestic statutory policy mission targeting near-zero fossil gas without CCUS by 2030.")
                                .interpretationCaveats(List.of("Domestic policy target; not a separate headline UNFCCC treaty metric."))
                                .legalBasisCitation("UK Department for Energy Security and Net Zero (DESNZ) / NESO Clean Power 2030")
                                .verificationDate("2026-10-28")
                                .build()
                ))
                .generalCaveats(List.of("UK baseline year is 1990 (not 2005)."))
                .provenance(unfcccProv)
                .build());

        // 4. United States (USA)
        // Formal Treaty Status: Withdrawn Non-Party (effective January 27, 2026 per Paris Agreement Article 28).
        // Historical Submissions: 2035 NDC deposited Dec 18, 2024 (-61% to -66% vs 2005); 2021 NDC (-50% to -52% vs 2005).
        NDC_REGISTRY.put("USA", CountryNdcProfileDto.builder()
                .countryCode("USA")
                .countryName("United States")
                .documentTitle("Historical U.S. 2035 NDC (Withdrawn) & Updated First NDC")
                .submissionDate("2024-12-18")
                .latestSubmissionDate("2024-12-18")
                .activeNdcCycle("Withdrawn (Effective January 27, 2026)")
                .previousSubmissionsSummary("First NDC (2015), Updated NDC (2021: 50–52% net GHG by 2030 vs 2005), 2035 NDC (deposited 2024-12-18). United States withdrawal from the Paris Agreement took effect on January 27, 2026.")
                .unfcccRegistryUrl("https://unfccc.int/NDCREG")
                .documentLanguage("English")
                .longTermGoal("National net-zero target not legally codified under active international treaty.")
                .targetYearPrimary(2035)
                .policyReviewStatus("WITHDRAWN_PARIS_AGREEMENT")
                .treatyParticipationStatus("WITHDRAWN_NON_PARTY")
                .treatyWithdrawalEffectiveDate("2026-01-27")
                .verificationDate("2026-10-28")
                .targets(List.of(
                        // Historical 2035 Target (Deposited prior to withdrawal)
                        NdcTargetDto.builder()
                                .id("USA-2035-T1")
                                .targetTitle("Economy-wide Net GHG Reduction (Historical 2035 Submission)")
                                .targetType("ABSOLUTE_REDUCTION")
                                .targetClassification("OFFICIAL_NDC_COMMITMENT")
                                .targetCurrencyStatus("HISTORICAL_SUPERSEDED_OR_WITHDRAWN")
                                .targetYear(2035)
                                .baselineYear(2005)
                                .commitmentStatus("UNCONDITIONAL")
                                .targetValue(61.0)
                                .targetRange("61% to 66%")
                                .targetUnit("PERCENT")
                                .sectorScope("ECONOMY_WIDE")
                                .gasesCovered(List.of("CO2", "CH4", "N2O", "HFCs", "PFCs", "SF6", "NF3"))
                                .officialDescription("Reduce net greenhouse gas emissions by 61 to 66 percent below 2005 levels by 2035. (Historical submission deposited December 18, 2024; treaty withdrawal took effect January 27, 2026).")
                                .powerSectorImplication("Historical benchmark; carries no active international legal force.")
                                .interpretationCaveats(List.of("TREATY WITHDRAWAL EFFECTIVE: Formal withdrawal under Article 28 entered into force on January 27, 2026. Target is preserved strictly as a historical benchmark."))
                                .legalBasisCitation("UNFCCC NDC Registry (Submission Date: 2024-12-18; Withdrawal Effective: 2026-01-27)")
                                .verificationDate("2026-10-28")
                                .build(),
                        // Historical 2030 Milestone
                        NdcTargetDto.builder()
                                .id("USA-2030-T1")
                                .targetTitle("Economy-wide Net GHG Reduction (Historical 2030 Target)")
                                .targetType("ABSOLUTE_REDUCTION")
                                .targetClassification("OFFICIAL_NDC_COMMITMENT")
                                .targetCurrencyStatus("HISTORICAL_SUPERSEDED_OR_WITHDRAWN")
                                .targetYear(2030)
                                .baselineYear(2005)
                                .commitmentStatus("UNCONDITIONAL")
                                .targetValue(50.0)
                                .targetRange("50% to 52%")
                                .targetUnit("PERCENT")
                                .sectorScope("ECONOMY_WIDE")
                                .officialDescription("Reduce net greenhouse gas emissions by 50 to 52 percent below 2005 levels by 2030 (2021 NDC Submission; withdrawn).")
                                .interpretationCaveats(List.of("Historical target preserved for baseline comparison; carries no active legal treaty force."))
                                .legalBasisCitation("UNFCCC NDC Registry (Submission Date: 2021-04-22; Withdrawal Effective: 2026-01-27)")
                                .verificationDate("2026-10-28")
                                .build(),
                        // Domestic Policy Target (Executive Order)
                        NdcTargetDto.builder()
                                .id("USA-DOM-CFE")
                                .targetTitle("Carbon Pollution-Free Electricity Goal (EO 14057)")
                                .targetType("RENEWABLE_SHARE_GENERATION")
                                .targetClassification("DOMESTIC_POLICY_LAW")
                                .targetCurrencyStatus("STATUTORY_DOMESTIC")
                                .targetYear(2035)
                                .baselineYear(null)
                                .commitmentStatus("UNCONDITIONAL")
                                .targetValue(100.0)
                                .targetUnit("PERCENT")
                                .sectorScope("POWER_SECTOR")
                                .officialDescription("100 percent carbon pollution-free electricity by 2035 under federal executive policy.")
                                .powerSectorImplication("Clean generation includes renewables, nuclear, and fossil CCS.")
                                .interpretationCaveats(List.of("Domestic executive policy goal subject to federal administrative revision; not an active UNFCCC treaty commitment."))
                                .legalBasisCitation("Executive Order 14057 (Catalyzing Clean Energy Industries)")
                                .verificationDate("2026-10-28")
                                .build()
                ))
                .generalCaveats(List.of(
                        "United States is a Non-Party to the Paris Agreement (withdrawal took effect January 27, 2026).",
                        "Historical targets are maintained for peer comparison but cannot be represented as active international commitments.",
                        "US baseline year is 2005 (not 1990)."
                ))
                .provenance(unfcccProv)
                .build());

        // 5. Brazil (BRA)
        // Latest Active: NDC 3.0 submitted Nov 2024 (-59% to -67% by 2035 vs 2005).
        // Historical: Updated NDC 2023 (-53.1% by 2030 vs 2005).
        NDC_REGISTRY.put("BRA", CountryNdcProfileDto.builder()
                .countryCode("BRA")
                .countryName("Brazil")
                .documentTitle("Brazil's Second Nationally Determined Contribution (NDC 3.0) & Updated NDC")
                .submissionDate("2024-11-13")
                .latestSubmissionDate("2024-11-13")
                .activeNdcCycle("NDC 3.0 (2035 Horizon)")
                .previousSubmissionsSummary("Updated NDC submitted 2023-11-03 (-53.1% by 2030 vs 2005).")
                .unfcccRegistryUrl("https://unfccc.int/NDCREG")
                .documentLanguage("Portuguese / Official English")
                .longTermGoal("Climate neutrality by 2050.")
                .targetYearPrimary(2035)
                .policyReviewStatus("VERIFIED_ACTIVE")
                .treatyParticipationStatus("PARTY_ACTIVE")
                .verificationDate("2026-10-28")
                .targets(List.of(
                        // Active Latest 2035 Target
                        NdcTargetDto.builder()
                                .id("BRA-2035-T1")
                                .targetTitle("Net GHG Reduction by 2035 (NDC 3.0)")
                                .targetType("ABSOLUTE_REDUCTION")
                                .targetClassification("OFFICIAL_NDC_COMMITMENT")
                                .targetCurrencyStatus("ACTIVE_LATEST_COMMITMENT")
                                .targetYear(2035)
                                .baselineYear(2005)
                                .commitmentStatus("UNCONDITIONAL")
                                .targetValue(59.0)
                                .targetRange("59% to 67%")
                                .targetUnit("PERCENT")
                                .sectorScope("ECONOMY_WIDE")
                                .officialDescription("Reduce net greenhouse gas emissions by 59 to 67 percent by 2035 below 2005 levels.")
                                .powerSectorImplication("Grid is already ~88% clean (hydro, wind, solar, biomass); primary national abatement relies on zero illegal Amazon deforestation.")
                                .interpretationCaveats(List.of("Abatement is heavily concentrated in the LULUCF forestry sector rather than electricity generation."))
                                .legalBasisCitation("UNFCCC NDC Registry (Submission Date: 2024-11-13)")
                                .verificationDate("2026-10-28")
                                .build(),
                        // Historical 2030 Milestone
                        NdcTargetDto.builder()
                                .id("BRA-2030-T1")
                                .targetTitle("Net GHG Reduction by 2030 (2030 Milestone)")
                                .targetType("ABSOLUTE_REDUCTION")
                                .targetClassification("OFFICIAL_NDC_COMMITMENT")
                                .targetCurrencyStatus("HISTORICAL_INTERMEDIATE_TARGET")
                                .targetYear(2030)
                                .baselineYear(2005)
                                .commitmentStatus("UNCONDITIONAL")
                                .targetValue(53.1)
                                .targetUnit("PERCENT")
                                .sectorScope("ECONOMY_WIDE")
                                .officialDescription("Reduce net greenhouse gas emissions by 53.1 percent by 2030 compared to 2005 levels.")
                                .interpretationCaveats(List.of("Intermediate 2030 milestone."))
                                .legalBasisCitation("UNFCCC NDC Registry (Submission Date: 2023-11-03)")
                                .verificationDate("2026-10-28")
                                .build()
                ))
                .generalCaveats(List.of("Brazil's power grid is already predominantly low-carbon; national NDC depends heavily on LULUCF."))
                .provenance(unfcccProv)
                .build());

        // 6. Canada (CAN)
        // Latest Active: 2035 NDC submitted Feb 2025 (-45% to -50% by 2035 vs 2005).
        // Historical: 2021 NDC (-40% to -45% by 2030 vs 2005).
        NDC_REGISTRY.put("CAN", CountryNdcProfileDto.builder()
                .countryCode("CAN")
                .countryName("Canada")
                .documentTitle("Canada's 2035 Nationally Determined Contribution & 2021 NDC")
                .submissionDate("2025-02-12")
                .latestSubmissionDate("2025-02-12")
                .activeNdcCycle("NDC 3.0 (2035 Horizon)")
                .previousSubmissionsSummary("2021 NDC pledged 40–45% reduction below 2005 by 2030.")
                .unfcccRegistryUrl("https://unfccc.int/NDCREG")
                .documentLanguage("English / French")
                .longTermGoal("Net-zero greenhouse gas emissions by 2050 (Net-Zero Emissions Accountability Act).")
                .targetYearPrimary(2035)
                .policyReviewStatus("VERIFIED_ACTIVE")
                .treatyParticipationStatus("PARTY_ACTIVE")
                .verificationDate("2026-10-28")
                .targets(List.of(
                        // Active Latest 2035 Target
                        NdcTargetDto.builder()
                                .id("CAN-2035-T1")
                                .targetTitle("Economy-wide GHG Reduction (2035 Target)")
                                .targetType("ABSOLUTE_REDUCTION")
                                .targetClassification("OFFICIAL_NDC_COMMITMENT")
                                .targetCurrencyStatus("ACTIVE_LATEST_COMMITMENT")
                                .targetYear(2035)
                                .baselineYear(2005)
                                .commitmentStatus("UNCONDITIONAL")
                                .targetValue(45.0)
                                .targetRange("45% to 50%")
                                .targetUnit("PERCENT")
                                .sectorScope("ECONOMY_WIDE")
                                .officialDescription("Reduce economy-wide emissions by 45 to 50 percent below 2005 levels by 2035.")
                                .powerSectorImplication("Supported by Clean Electricity Regulations targeting net-zero electricity grid by 2035.")
                                .interpretationCaveats(List.of("Baseline year is 2005. Oil and gas upstream extraction is a major non-power emissions source."))
                                .legalBasisCitation("UNFCCC NDC Registry (Submission Date: 2025-02-12)")
                                .verificationDate("2026-10-28")
                                .build(),
                        // Historical 2030 Milestone
                        NdcTargetDto.builder()
                                .id("CAN-2030-T1")
                                .targetTitle("Economy-wide GHG Reduction (2030 Milestone)")
                                .targetType("ABSOLUTE_REDUCTION")
                                .targetClassification("OFFICIAL_NDC_COMMITMENT")
                                .targetCurrencyStatus("HISTORICAL_INTERMEDIATE_TARGET")
                                .targetYear(2030)
                                .baselineYear(2005)
                                .commitmentStatus("UNCONDITIONAL")
                                .targetValue(40.0)
                                .targetRange("40% to 45%")
                                .targetUnit("PERCENT")
                                .sectorScope("ECONOMY_WIDE")
                                .officialDescription("Reduce economy-wide emissions by 40 to 45 percent below 2005 levels by 2030.")
                                .interpretationCaveats(List.of("Intermediate 2030 milestone."))
                                .legalBasisCitation("UNFCCC NDC Registry (Submission Date: 2021-07-12)")
                                .verificationDate("2026-10-28")
                                .build()
                ))
                .generalCaveats(List.of("Canadian electricity is already ~80% clean (hydro/nuclear); remaining fossil reduction is focused in Alberta and Saskatchewan."))
                .provenance(unfcccProv)
                .build());

        // 7. Australia (AUS)
        // Latest Active: 2035 NDC submitted Sept 2025 (-62% to -70% by 2035 vs 2005).
        // Historical: 2022 NDC (-43% by 2030 vs 2005).
        NDC_REGISTRY.put("AUS", CountryNdcProfileDto.builder()
                .countryCode("AUS")
                .countryName("Australia")
                .documentTitle("Australia's 2035 Nationally Determined Contribution & 2022 NDC")
                .submissionDate("2025-09-18")
                .latestSubmissionDate("2025-09-18")
                .activeNdcCycle("NDC 3.0 (2035 Horizon)")
                .previousSubmissionsSummary("2022 NDC pledged 43% reduction below 2005 by 2030.")
                .unfcccRegistryUrl("https://unfccc.int/NDCREG")
                .documentLanguage("English")
                .longTermGoal("Net zero emissions by 2050 (Climate Change Act 2022).")
                .targetYearPrimary(2035)
                .policyReviewStatus("VERIFIED_ACTIVE")
                .treatyParticipationStatus("PARTY_ACTIVE")
                .verificationDate("2026-10-28")
                .targets(List.of(
                        // Active Latest 2035 Target
                        NdcTargetDto.builder()
                                .id("AUS-2035-T1")
                                .targetTitle("Economy-wide Net GHG Reduction (2035 Target)")
                                .targetType("ABSOLUTE_REDUCTION")
                                .targetClassification("OFFICIAL_NDC_COMMITMENT")
                                .targetCurrencyStatus("ACTIVE_LATEST_COMMITMENT")
                                .targetYear(2035)
                                .baselineYear(2005)
                                .commitmentStatus("UNCONDITIONAL")
                                .targetValue(62.0)
                                .targetRange("62% to 70%")
                                .targetUnit("PERCENT")
                                .sectorScope("ECONOMY_WIDE")
                                .officialDescription("Reduce net greenhouse gas emissions by 62 to 70 percent below 2005 levels by 2035.")
                                .powerSectorImplication("Accelerates coal exit across the National Electricity Market (NEM).")
                                .interpretationCaveats(List.of("Includes LULUCF land carbon accounting."))
                                .legalBasisCitation("UNFCCC NDC Registry (Submission Date: 2025-09-18)")
                                .verificationDate("2026-10-28")
                                .build(),
                        // Historical 2030 Milestone
                        NdcTargetDto.builder()
                                .id("AUS-2030-T1")
                                .targetTitle("Economy-wide Net GHG Reduction (2030 Milestone)")
                                .targetType("ABSOLUTE_REDUCTION")
                                .targetClassification("OFFICIAL_NDC_COMMITMENT")
                                .targetCurrencyStatus("HISTORICAL_INTERMEDIATE_TARGET")
                                .targetYear(2030)
                                .baselineYear(2005)
                                .commitmentStatus("UNCONDITIONAL")
                                .targetValue(43.0)
                                .targetUnit("PERCENT")
                                .sectorScope("ECONOMY_WIDE")
                                .officialDescription("Reduce greenhouse gas emissions by 43 percent below 2005 levels by 2030.")
                                .interpretationCaveats(List.of("Intermediate 2030 milestone."))
                                .legalBasisCitation("UNFCCC NDC Registry (Submission Date: 2022-06-16)")
                                .verificationDate("2026-10-28")
                                .build(),
                        // Domestic Policy Target
                        NdcTargetDto.builder()
                                .id("AUS-DOM-NEM")
                                .targetTitle("Renewable Electricity Share in NEM (Domestic Policy)")
                                .targetType("RENEWABLE_SHARE_GENERATION")
                                .targetClassification("DOMESTIC_POLICY_LAW")
                                .targetCurrencyStatus("STATUTORY_DOMESTIC")
                                .targetYear(2030)
                                .baselineYear(null)
                                .commitmentStatus("UNCONDITIONAL")
                                .targetValue(82.0)
                                .targetUnit("PERCENT")
                                .sectorScope("POWER_SECTOR")
                                .officialDescription("Reach 82 percent renewable electricity generation by 2030 in the National Electricity Market under Rewiring the Nation.")
                                .powerSectorImplication("High generation share target directly comparable with simulated TWh.")
                                .interpretationCaveats(List.of("Domestic policy roadmap; not an explicit headline NDC treaty commitment."))
                                .legalBasisCitation("Rewiring the Nation / National Electricity Market Transition Plan")
                                .verificationDate("2026-10-28")
                                .build()
                ))
                .generalCaveats(List.of("Australia baseline is 2005."))
                .provenance(unfcccProv)
                .build());

        // 8. Japan (JPN)
        // Latest Active: 2035 NDC submitted Feb 2025 (-60% by FY2035 vs FY2013).
        // Historical: 2021 NDC (-46% by FY2030 vs FY2013).
        NDC_REGISTRY.put("JPN", CountryNdcProfileDto.builder()
                .countryCode("JPN")
                .countryName("Japan")
                .documentTitle("Japan's 2035 Nationally Determined Contribution & 2021 NDC")
                .submissionDate("2025-02-21")
                .latestSubmissionDate("2025-02-21")
                .activeNdcCycle("NDC 3.0 (2035 Horizon)")
                .previousSubmissionsSummary("2021 NDC pledged 46% reduction in FY2030 vs FY2013.")
                .unfcccRegistryUrl("https://unfccc.int/NDCREG")
                .documentLanguage("Japanese / Official English")
                .longTermGoal("Net-zero greenhouse gas emissions by 2050.")
                .targetYearPrimary(2035)
                .policyReviewStatus("VERIFIED_ACTIVE")
                .treatyParticipationStatus("PARTY_ACTIVE")
                .verificationDate("2026-10-28")
                .targets(List.of(
                        // Active Latest 2035 Target
                        NdcTargetDto.builder()
                                .id("JPN-2035-T1")
                                .targetTitle("Economy-wide GHG Reduction (FY2035 Target)")
                                .targetType("ABSOLUTE_REDUCTION")
                                .targetClassification("OFFICIAL_NDC_COMMITMENT")
                                .targetCurrencyStatus("ACTIVE_LATEST_COMMITMENT")
                                .targetYear(2035)
                                .baselineYear(2013)
                                .commitmentStatus("UNCONDITIONAL")
                                .targetValue(60.0)
                                .targetUnit("PERCENT")
                                .sectorScope("ECONOMY_WIDE")
                                .officialDescription("Reduce greenhouse gas emissions by 60 percent in FY2035 compared to FY2013 levels (aiming for 73% by 2040).")
                                .powerSectorImplication("Accelerated phaseout of unabated coal, restart of nuclear units, and expansion of offshore wind.")
                                .interpretationCaveats(List.of("Baseline year is FY2013 (Japan fiscal year)."))
                                .legalBasisCitation("UNFCCC NDC Registry (Submission Date: 2025-02-21)")
                                .verificationDate("2026-10-28")
                                .build(),
                        // Historical 2030 Milestone
                        NdcTargetDto.builder()
                                .id("JPN-2030-T1")
                                .targetTitle("Economy-wide GHG Reduction (FY2030 Milestone)")
                                .targetType("ABSOLUTE_REDUCTION")
                                .targetClassification("OFFICIAL_NDC_COMMITMENT")
                                .targetCurrencyStatus("HISTORICAL_INTERMEDIATE_TARGET")
                                .targetYear(2030)
                                .baselineYear(2013)
                                .commitmentStatus("UNCONDITIONAL")
                                .targetValue(46.0)
                                .targetRange("46% to 50%")
                                .targetUnit("PERCENT")
                                .sectorScope("ECONOMY_WIDE")
                                .officialDescription("Reduce greenhouse gas emissions by 46 percent in FY2030 from FY2013 levels.")
                                .interpretationCaveats(List.of("Intermediate milestone."))
                                .legalBasisCitation("UNFCCC NDC Registry (Submission Date: 2021-10-22)")
                                .verificationDate("2026-10-28")
                                .build(),
                        // Domestic Policy Target
                        NdcTargetDto.builder()
                                .id("JPN-DOM-SEP")
                                .targetTitle("Renewable Electricity Share (6th Strategic Energy Plan)")
                                .targetType("RENEWABLE_SHARE_GENERATION")
                                .targetClassification("DOMESTIC_POLICY_LAW")
                                .targetCurrencyStatus("STATUTORY_DOMESTIC")
                                .targetYear(2030)
                                .baselineYear(null)
                                .commitmentStatus("UNCONDITIONAL")
                                .targetValue(37.0)
                                .targetRange("36% to 38%")
                                .targetUnit("PERCENT")
                                .sectorScope("POWER_SECTOR")
                                .officialDescription("Renewable energy generation share of 36 to 38 percent in power mix by 2030 under Japan's 6th Strategic Energy Plan.")
                                .powerSectorImplication("Explicit generation share target (TWh basis).")
                                .interpretationCaveats(List.of("Domestic policy target; not an explicit headline NDC treaty metric."))
                                .legalBasisCitation("Ministry of Economy, Trade and Industry (METI) 6th Strategic Energy Plan")
                                .verificationDate("2026-10-28")
                                .build()
                ))
                .generalCaveats(List.of("Baseline is FY2013."))
                .provenance(unfcccProv)
                .build());

        // 9. China (CHN)
        // Latest Active: 2035 NDC submitted 2025-11-03 (-7% to -10% absolute net GHG reduction below peak; >3600 GW wind/solar).
        // Historical: Updated NDC 2021 (peaking before 2030; -65% CO2 intensity vs 2005; >1200 GW wind/solar achieved in 2024).
        NDC_REGISTRY.put("CHN", CountryNdcProfileDto.builder()
                .countryCode("CHN")
                .countryName("China")
                .documentTitle("China's 2035 Nationally Determined Contribution & Updated First NDC")
                .submissionDate("2025-11-03")
                .latestSubmissionDate("2025-11-03")
                .activeNdcCycle("NDC 3.0 (2035 Horizon)")
                .previousSubmissionsSummary("Initial NDC submitted 2016-09-03. Updated First NDC submitted 2021-10-28 (carbon peak before 2030; -65% CO2 intensity of GDP vs 2005; 1,200 GW wind/solar achieved early in 2024).")
                .unfcccRegistryUrl("https://unfccc.int/NDCREG")
                .documentLanguage("Chinese / Official English")
                .longTermGoal("Achieve carbon neutrality before 2060.")
                .targetYearPrimary(2035)
                .policyReviewStatus("VERIFIED_ACTIVE")
                .treatyParticipationStatus("PARTY_ACTIVE")
                .verificationDate("2026-10-28")
                .targets(List.of(
                        // Active Latest 2035 Targets (NDC 3.0)
                        NdcTargetDto.builder()
                                .id("CHN-2035-T1")
                                .targetTitle("Economy-wide Net GHG Reduction Below Peak (2035 Target)")
                                .targetType("ABSOLUTE_REDUCTION")
                                .targetClassification("OFFICIAL_NDC_COMMITMENT")
                                .targetCurrencyStatus("ACTIVE_LATEST_COMMITMENT")
                                .targetYear(2035)
                                .baselineYear(null)
                                .commitmentStatus("UNCONDITIONAL")
                                .targetValue(7.0)
                                .targetRange("7% to 10% below peak")
                                .targetUnit("PERCENT")
                                .sectorScope("ECONOMY_WIDE")
                                .gasesCovered(List.of("CO2", "CH4", "N2O", "HFCs"))
                                .officialDescription("Reduce economy-wide net greenhouse gas emissions by 7 to 10 percent below peak emission levels by 2035 (China's first economy-wide absolute reduction commitment).")
                                .powerSectorImplication("Decoupling coal generation from demand growth and scaling storage integration.")
                                .interpretationCaveats(List.of("Target is measured relative to China's emissions peak rather than a fixed historical year."))
                                .legalBasisCitation("UNFCCC NDC Registry (Submission Date: 2025-11-03)")
                                .verificationDate("2026-10-28")
                                .build(),
                        NdcTargetDto.builder()
                                .id("CHN-2035-T2")
                                .targetTitle("Wind and Solar Installed Electric Capacity (2035 Target)")
                                .targetType("RENEWABLE_SHARE_CAPACITY")
                                .targetClassification("OFFICIAL_NDC_COMMITMENT")
                                .targetCurrencyStatus("ACTIVE_LATEST_COMMITMENT")
                                .targetYear(2035)
                                .baselineYear(null)
                                .commitmentStatus("UNCONDITIONAL")
                                .targetValue(3600.0)
                                .targetUnit("GW")
                                .sectorScope("POWER_SECTOR")
                                .gasesCovered(List.of("CO2"))
                                .officialDescription("Expand total installed capacity of wind and solar power to over 3,600 GW (3.6 TW) by 2035.")
                                .powerSectorImplication("Massive renewable buildout requiring UHVDC long-distance transmission from western desert bases.")
                                .interpretationCaveats(List.of("Capacity metric in GW; generation in TWh depends on curtailment and energy storage deployment."))
                                .legalBasisCitation("UNFCCC NDC Registry (Submission Date: 2025-11-03)")
                                .verificationDate("2026-10-28")
                                .build(),
                        NdcTargetDto.builder()
                                .id("CHN-2035-T3")
                                .targetTitle("Non-Fossil Share of Primary Energy Consumption (2035 Target)")
                                .targetType("RENEWABLE_SHARE_GENERATION")
                                .targetClassification("OFFICIAL_NDC_COMMITMENT")
                                .targetCurrencyStatus("ACTIVE_LATEST_COMMITMENT")
                                .targetYear(2035)
                                .baselineYear(null)
                                .commitmentStatus("UNCONDITIONAL")
                                .targetValue(30.0)
                                .targetUnit("PERCENT")
                                .sectorScope("ENERGY_SECTOR")
                                .officialDescription("Increase non-fossil energy supply to over 30 percent of total primary energy consumption by 2035.")
                                .powerSectorImplication("Primary energy metric including nuclear, hydro, solar, and wind.")
                                .interpretationCaveats(List.of("Primary energy consumption basis; differs from electricity generation share."))
                                .legalBasisCitation("UNFCCC NDC Registry (Submission Date: 2025-11-03)")
                                .verificationDate("2026-10-28")
                                .build(),
                        // Historical 2030 Milestones
                        NdcTargetDto.builder()
                                .id("CHN-2030-T1")
                                .targetTitle("Peak Carbon Dioxide Emissions (2030 Milestone)")
                                .targetType("FIXED_EMISSIONS_RANGE")
                                .targetClassification("OFFICIAL_NDC_COMMITMENT")
                                .targetCurrencyStatus("HISTORICAL_INTERMEDIATE_TARGET")
                                .targetYear(2030)
                                .baselineYear(null)
                                .commitmentStatus("UNCONDITIONAL")
                                .officialDescription("Strictly peak carbon dioxide emissions before 2030.")
                                .interpretationCaveats(List.of("Historical milestone from 2021 NDC."))
                                .legalBasisCitation("UNFCCC NDC Registry (Submission Date: 2021-10-28)")
                                .verificationDate("2026-10-28")
                                .build(),
                        NdcTargetDto.builder()
                                .id("CHN-2030-T2")
                                .targetTitle("CO2 Intensity Reduction of GDP (2030 Milestone)")
                                .targetType("INTENSITY_GDP")
                                .targetClassification("OFFICIAL_NDC_COMMITMENT")
                                .targetCurrencyStatus("HISTORICAL_INTERMEDIATE_TARGET")
                                .targetYear(2030)
                                .baselineYear(2005)
                                .commitmentStatus("UNCONDITIONAL")
                                .targetValue(65.0)
                                .targetUnit("PERCENT")
                                .sectorScope("ECONOMY_WIDE")
                                .officialDescription("Lower carbon dioxide emissions per unit of GDP by over 65 percent from 2005 levels.")
                                .interpretationCaveats(List.of("Intermediate 2030 milestone."))
                                .legalBasisCitation("UNFCCC NDC Registry (Submission Date: 2021-10-28)")
                                .verificationDate("2026-10-28")
                                .build(),
                        NdcTargetDto.builder()
                                .id("CHN-2030-T3")
                                .targetTitle("Wind and Solar Installed Capacity (2030 Milestone - Achieved Early)")
                                .targetType("RENEWABLE_SHARE_CAPACITY")
                                .targetClassification("OFFICIAL_NDC_COMMITMENT")
                                .targetCurrencyStatus("HISTORICAL_INTERMEDIATE_TARGET")
                                .targetYear(2030)
                                .baselineYear(null)
                                .commitmentStatus("UNCONDITIONAL")
                                .targetValue(1200.0)
                                .targetUnit("GW")
                                .sectorScope("POWER_SECTOR")
                                .officialDescription("Total installed capacity of wind and solar power to over 1,200 GW by 2030 (achieved ahead of schedule in 2024).")
                                .interpretationCaveats(List.of("Surpassed in 2024; superseded by 3,600 GW 2035 target."))
                                .legalBasisCitation("UNFCCC NDC Registry (Submission Date: 2021-10-28)")
                                .verificationDate("2026-10-28")
                                .build()
                ))
                .generalCaveats(List.of("China's 2035 NDC includes multi-gas emissions (CO2, CH4, N2O, HFCs), expanding upon earlier CO2-only targets."))
                .provenance(unfcccProv)
                .build());

        // 10. South Africa (ZAF)
        // Latest Active: Second NDC submitted October 2025 (320–380 Mt CO2e by 2035).
        // Historical: Updated First NDC 2021 (350–420 Mt CO2e by 2030).
        NDC_REGISTRY.put("ZAF", CountryNdcProfileDto.builder()
                .countryCode("ZAF")
                .countryName("South Africa")
                .documentTitle("South Africa's Second Nationally Determined Contribution (2031–2035) & Updated NDC")
                .submissionDate("2025-10-15")
                .latestSubmissionDate("2025-10-15")
                .activeNdcCycle("Second NDC (2031–2035 Horizon)")
                .previousSubmissionsSummary("Updated First NDC submitted 2021-09-27 (350–420 Mt CO2e by 2030; JETP partnership established at COP26).")
                .unfcccRegistryUrl("https://unfccc.int/NDCREG")
                .documentLanguage("English")
                .longTermGoal("Net-zero greenhouse gas emissions by 2050 (Just Energy Transition Partnership).")
                .targetYearPrimary(2035)
                .policyReviewStatus("VERIFIED_ACTIVE")
                .treatyParticipationStatus("PARTY_ACTIVE")
                .verificationDate("2026-10-28")
                .targets(List.of(
                        // Active Latest 2035 Target (Second NDC)
                        NdcTargetDto.builder()
                                .id("ZAF-2035-T1")
                                .targetTitle("Absolute Annual GHG Emissions Range (2035 Target)")
                                .targetType("FIXED_EMISSIONS_RANGE")
                                .targetClassification("OFFICIAL_NDC_COMMITMENT")
                                .targetCurrencyStatus("ACTIVE_LATEST_COMMITMENT")
                                .targetYear(2035)
                                .baselineYear(null)
                                .commitmentStatus("COMBINED")
                                .targetValue(380.0)
                                .targetValueConditional(320.0)
                                .targetRange("320 to 380 Mt CO2e")
                                .targetUnit("MT_CO2E")
                                .sectorScope("ECONOMY_WIDE")
                                .officialDescription("Annual GHG emissions will be in a range from 320 to 380 Mt CO2e in 2035 (380 Mt unconditional; 320 Mt conditional on international climate finance).")
                                .powerSectorImplication("Requires accelerated decommissioning of aging Eskom coal units and scaling up transmission grid capacity.")
                                .interpretationCaveats(List.of("Fixed numeric target range in absolute Mt CO2e."))
                                .legalBasisCitation("UNFCCC NDC Registry (Submission Date: 2025-10-15)")
                                .verificationDate("2026-10-28")
                                .build(),
                        // Historical 2030 Milestone
                        NdcTargetDto.builder()
                                .id("ZAF-2030-T1")
                                .targetTitle("Fixed Absolute Emissions Ceiling (2030 Milestone)")
                                .targetType("FIXED_EMISSIONS_RANGE")
                                .targetClassification("OFFICIAL_NDC_COMMITMENT")
                                .targetCurrencyStatus("HISTORICAL_INTERMEDIATE_TARGET")
                                .targetYear(2030)
                                .baselineYear(null)
                                .commitmentStatus("COMBINED")
                                .targetValue(420.0)
                                .targetValueConditional(350.0)
                                .targetRange("350 to 420 Mt CO2e")
                                .targetUnit("MT_CO2E")
                                .sectorScope("ECONOMY_WIDE")
                                .officialDescription("Annual GHG emissions will be in a range from 350 to 420 Mt CO2e in 2030.")
                                .interpretationCaveats(List.of("Intermediate 2030 milestone from Updated First NDC."))
                                .legalBasisCitation("UNFCCC NDC Registry (Submission Date: 2021-09-27)")
                                .verificationDate("2026-10-28")
                                .build()
                ))
                .generalCaveats(List.of("Contains an explicit conditional ceiling dependent on international JETP climate finance and Eskom debt restructuring."))
                .provenance(unfcccProv)
                .build());

        // 11. Indonesia (IDN)
        // Latest Active: Second NDC (SNDC) submitted 2025-10-27 (transitioning to 2019 baseline; 1.26–1.49 Gt CO2e by 2035).
        // Historical: Enhanced NDC submitted 2022-09-23 (-31.89% unconditional, -43.20% conditional vs 2030 BAU).
        // Multilateral: JETP target of 44% RE generation by 2030.
        NDC_REGISTRY.put("IDN", CountryNdcProfileDto.builder()
                .countryCode("IDN")
                .countryName("Indonesia")
                .documentTitle("Second Nationally Determined Contribution (SNDC) of Republic of Indonesia & Enhanced NDC")
                .submissionDate("2025-10-27")
                .latestSubmissionDate("2025-10-27")
                .activeNdcCycle("Second NDC (SNDC: 2031–2035 Horizon)")
                .previousSubmissionsSummary("First NDC (2016), Updated First NDC (2021), Enhanced NDC (submitted 2022-09-23: -31.89% unconditional, -43.20% conditional vs 2030 BAU). SNDC transitioned to a fixed 2019 baseline.")
                .unfcccRegistryUrl("https://unfccc.int/NDCREG")
                .documentLanguage("English / Indonesian")
                .longTermGoal("Net-zero emissions by 2060 or sooner (aligned with LTS-LCCR 2050 and RPJPN 2025–2045).")
                .targetYearPrimary(2035)
                .policyReviewStatus("VERIFIED_ACTIVE")
                .treatyParticipationStatus("PARTY_ACTIVE")
                .verificationDate("2026-10-28")
                .targets(List.of(
                        // Active Latest 2035 Target (Second NDC)
                        NdcTargetDto.builder()
                                .id("IDN-2035-T1")
                                .targetTitle("Absolute Economy-wide GHG Emissions Cap (2035 Target)")
                                .targetType("FIXED_EMISSIONS_RANGE")
                                .targetClassification("OFFICIAL_NDC_COMMITMENT")
                                .targetCurrencyStatus("ACTIVE_LATEST_COMMITMENT")
                                .targetYear(2035)
                                .baselineYear(2019)
                                .commitmentStatus("COMBINED")
                                .targetValue(1490.0)
                                .targetValueConditional(1260.0)
                                .targetRange("1.26 to 1.49 Gt CO2e")
                                .targetUnit("GT_CO2E")
                                .sectorScope("ECONOMY_WIDE")
                                .gasesCovered(List.of("CO2", "CH4", "N2O", "HFCs"))
                                .officialDescription("Cap absolute national greenhouse gas emissions between 1.26 and 1.49 Gt CO2e by 2035 against a fixed 2019 baseline (1,145 Mt CO2e), supported by carbon pricing under Presidential Regulation No. 110 of 2025.")
                                .powerSectorImplication("Supported by early retirement of subcritical coal plants and scaling renewable grid additions under PLN RUPTL.")
                                .interpretationCaveats(List.of("Second NDC transitions away from counterfactual BAU to a fixed 2019 baseline."))
                                .legalBasisCitation("UNFCCC Second NDC Submission (Submission Date: 2025-10-27)")
                                .verificationDate("2026-10-28")
                                .build(),
                        // 2030 Peaking Benchmark (Second NDC)
                        NdcTargetDto.builder()
                                .id("IDN-2030-PEAK")
                                .targetTitle("Economy-wide GHG Emissions Peaking (2030 Benchmark)")
                                .targetType("FIXED_EMISSIONS_RANGE")
                                .targetClassification("OFFICIAL_NDC_COMMITMENT")
                                .targetCurrencyStatus("ACTIVE_LATEST_COMMITMENT")
                                .targetYear(2030)
                                .baselineYear(2019)
                                .commitmentStatus("UNCONDITIONAL")
                                .targetRange("1.35 to 1.49 Gt CO2e")
                                .targetUnit("GT_CO2E")
                                .sectorScope("ECONOMY_WIDE")
                                .officialDescription("National emissions peak around 2030 at 1.35 to 1.49 Gt CO2e before declining toward 2035.")
                                .powerSectorImplication("Requires peaking on-grid power emissions in the late 2020s.")
                                .interpretationCaveats(List.of("Trajectory benchmark under Indonesia's Second NDC."))
                                .legalBasisCitation("UNFCCC Second NDC Submission (Submission Date: 2025-10-27)")
                                .verificationDate("2026-10-28")
                                .build(),
                        // Historical 2030 Milestone (Enhanced NDC 2022)
                        NdcTargetDto.builder()
                                .id("IDN-2030-HIST")
                                .targetTitle("GHG Reduction vs. Business-As-Usual (2030 Milestone)")
                                .targetType("DEVIATION_FROM_BAU")
                                .targetClassification("OFFICIAL_NDC_COMMITMENT")
                                .targetCurrencyStatus("HISTORICAL_INTERMEDIATE_TARGET")
                                .targetYear(2030)
                                .baselineYear(null)
                                .commitmentStatus("COMBINED")
                                .targetValue(31.89)
                                .targetValueConditional(43.20)
                                .targetRange("31.89% unconditional, 43.20% conditional")
                                .targetUnit("PERCENT")
                                .sectorScope("ECONOMY_WIDE")
                                .officialDescription("Historical Enhanced NDC target: reduce emissions by 31.89% unconditionally and up to 43.20% conditionally against 2030 BAU baseline (2,869 Mt CO2e).")
                                .powerSectorImplication("Historical benchmark from 2022 submission cycle.")
                                .interpretationCaveats(List.of("Measured against 2030 counterfactual BAU baseline."))
                                .legalBasisCitation("UNFCCC Enhanced NDC (Submission Date: 2022-09-23)")
                                .verificationDate("2026-10-28")
                                .build(),
                        // Multilateral Partnership Target (JETP)
                        NdcTargetDto.builder()
                                .id("IDN-DOM-JETP")
                                .targetTitle("JETP Power Sector Peaking & 44% RE Generation Target")
                                .targetType("RENEWABLE_SHARE_GENERATION")
                                .targetClassification("OTHER_OFFICIAL_SOURCE")
                                .targetCurrencyStatus("STATUTORY_DOMESTIC")
                                .targetYear(2030)
                                .baselineYear(null)
                                .commitmentStatus("CONDITIONAL")
                                .targetValue(44.0)
                                .targetUnit("PERCENT")
                                .sectorScope("POWER_SECTOR")
                                .officialDescription("Peaking on-grid power sector emissions at 290 Mt CO2e and reaching 44 percent renewable electricity generation by 2030 under the JETP Comprehensive Investment and Policy Plan (CIPP).")
                                .powerSectorImplication("Explicit power sector generation percentage directly comparable with simulated TWh.")
                                .interpretationCaveats(List.of("Multilateral climate partnership roadmap; conditional on $20B international public and private financing."))
                                .legalBasisCitation("Indonesia JETP Comprehensive Investment and Policy Plan (CIPP 2023)")
                                .verificationDate("2026-10-28")
                                .build()
                ))
                .generalCaveats(List.of(
                        "Second NDC transitions Indonesia from a counterfactual BAU to a fixed 2019 emissions baseline.",
                        "Power sector targets are supported by the multilateral JETP partnership and PLN green energy supply plan."
                ))
                .provenance(unfcccProv)
                .build());

        // 12. Vietnam (VNM)
        // Latest Active: Updated NDC 2022 (-15.8% unconditional, -43.5% conditional vs 2030 BAU).
        // Draft NDC 3.0 (-41.7% by 2035 vs BAU) in domestic consultations prior to formal UNFCCC registry deposit.
        NDC_REGISTRY.put("VNM", CountryNdcProfileDto.builder()
                .countryCode("VNM")
                .countryName("Vietnam")
                .documentTitle("Viet Nam's Updated Nationally Determined Contribution")
                .submissionDate("2022-11-08")
                .latestSubmissionDate("2022-11-08")
                .activeNdcCycle("Updated NDC (2030 Horizon; NDC 3.0 in Consultation)")
                .previousSubmissionsSummary("Updated NDC submitted 2022-11-08. Draft NDC 3.0 proposing -41.7% by 2035 vs BAU is undergoing domestic consultations prior to formal UNFCCC deposit.")
                .unfcccRegistryUrl("https://unfccc.int/NDCREG")
                .documentLanguage("English / Vietnamese")
                .longTermGoal("Net zero emissions by 2050 (Power Development Plan 8 / PDP8).")
                .targetYearPrimary(2030)
                .policyReviewStatus("VERIFIED_ACTIVE")
                .treatyParticipationStatus("PARTY_ACTIVE")
                .verificationDate("2026-10-28")
                .targets(List.of(
                        NdcTargetDto.builder()
                                .id("VNM-T1")
                                .targetTitle("GHG Reduction vs. BAU Baseline")
                                .targetType("DEVIATION_FROM_BAU")
                                .targetClassification("OFFICIAL_NDC_COMMITMENT")
                                .targetCurrencyStatus("ACTIVE_LATEST_COMMITMENT")
                                .targetYear(2030)
                                .baselineYear(null)
                                .commitmentStatus("COMBINED")
                                .targetValue(15.8)
                                .targetValueConditional(43.5)
                                .targetRange("15.8% unconditional, 43.5% conditional")
                                .targetUnit("PERCENT")
                                .sectorScope("ECONOMY_WIDE")
                                .officialDescription("Reduce GHG emissions by 15.8 percent unconditionally and 43.5 percent conditionally compared to BAU in 2030.")
                                .powerSectorImplication("PDP8 & JETP plan for peaking power sector emissions by 2030.")
                                .interpretationCaveats(List.of("Evaluated against 2030 BAU baseline."))
                                .legalBasisCitation("UNFCCC NDC Registry (Submission Date: 2022-11-08)")
                                .verificationDate("2026-10-28")
                                .build()
                ))
                .generalCaveats(List.of("Evaluated against BAU baseline."))
                .provenance(unfcccProv)
                .build());
    }

    private static void registerEuMember(String code, String name, String nationalPolicyName,
                                         Double nationalGhgReductionTarget, Double powerCleanShareTarget,
                                         String longTerm, ProvenanceDto prov) {
        List<NdcTargetDto> targets = new ArrayList<>();

        // Target 1: Joint EU NDC 2030 Commitment (Binding under Fit for 55)
        targets.add(NdcTargetDto.builder()
                .id(code + "-EU-2030")
                .targetTitle("EU Joint Fulfilment: Net Domestic GHG Reduction (2030 Commitment)")
                .targetType("ABSOLUTE_REDUCTION")
                .targetClassification("JOINT_EU_NDC_COMMITMENT")
                .targetCurrencyStatus("HISTORICAL_INTERMEDIATE_TARGET")
                .targetYear(2030)
                .baselineYear(1990)
                .commitmentStatus("UNCONDITIONAL")
                .targetValue(55.0)
                .targetUnit("PERCENT")
                .sectorScope("ECONOMY_WIDE")
                .gasesCovered(List.of("CO2", "CH4", "N2O", "HFCs", "PFCs", "SF6", "NF3"))
                .officialDescription("Reduce net domestic greenhouse gas emissions by at least 55 percent by 2030 compared to 1990 levels across the 27 EU member states (Fit for 55).")
                .powerSectorImplication("Governed by EU Emissions Trading System (EU ETS) with declining cap for power generators.")
                .interpretationCaveats(List.of("Joint fulfilment across 27 EU member states; not an identical, independently submitted national target."))
                .legalBasisCitation("EU NDC Update (UNFCCC Interim Registry)")
                .verificationDate("2026-10-28")
                .build());

        // Target 2: Joint EU NDC 2035 Indicative Milestone (Submitted 2025)
        targets.add(NdcTargetDto.builder()
                .id(code + "-EU-2035")
                .targetTitle("EU Joint Fulfilment: Indicative Net GHG Reduction (2035 Milestone)")
                .targetType("ABSOLUTE_REDUCTION")
                .targetClassification("JOINT_EU_NDC_COMMITMENT")
                .targetCurrencyStatus("ACTIVE_LATEST_COMMITMENT")
                .targetYear(2035)
                .baselineYear(1990)
                .commitmentStatus("UNCONDITIONAL")
                .targetValue(66.25)
                .targetRange("66.25% to 72.5%")
                .targetUnit("PERCENT")
                .sectorScope("ECONOMY_WIDE")
                .gasesCovered(List.of("CO2", "CH4", "N2O", "HFCs", "PFCs", "SF6", "NF3"))
                .officialDescription("Indicative net greenhouse gas emission reduction of 66.25 to 72.5 percent below 1990 levels across the EU by 2035 (2025 NDC Update).")
                .powerSectorImplication("Near-complete phase-out of unabated coal across EU power grids.")
                .interpretationCaveats(List.of("EU joint commitment trajectory bridging 2030 Fit for 55 and the 2040 90% framework."))
                .legalBasisCitation("Update of the NDC of the EU and its Member States (2025 Submission)")
                .verificationDate("2026-10-28")
                .build());

        // Target 3: Separate National Statutory Target (Domestic Law / Energy Policy)
        if (powerCleanShareTarget != null) {
            targets.add(NdcTargetDto.builder()
                    .id(code + "-DOM-POWER")
                    .targetTitle("National Renewable Electricity Target (Domestic Law)")
                    .targetType("RENEWABLE_SHARE_GENERATION")
                    .targetClassification("DOMESTIC_POLICY_LAW")
                    .targetCurrencyStatus("STATUTORY_DOMESTIC")
                    .targetYear(2030)
                    .baselineYear(null)
                    .commitmentStatus("UNCONDITIONAL")
                    .targetValue(powerCleanShareTarget)
                    .targetUnit("PERCENT")
                    .sectorScope("POWER_SECTOR")
                    .gasesCovered(List.of("CO2"))
                    .officialDescription(String.format("Reach %.1f percent renewable generation in the national electricity mix by 2030 under %s.", powerCleanShareTarget, nationalPolicyName))
                    .powerSectorImplication("Explicit national power sector clean generation share.")
                    .interpretationCaveats(List.of("Domestic policy target under national law; not a standalone national NDC treaty filing."))
                    .legalBasisCitation(nationalPolicyName)
                    .verificationDate("2026-10-28")
                    .build());
        }

        NDC_REGISTRY.put(code, CountryNdcProfileDto.builder()
                .countryCode(code)
                .countryName(name)
                .documentTitle("Update of the NDC of the European Union and its Member States (Joint Fulfilment)")
                .submissionDate("2025-03-15")
                .latestSubmissionDate("2025-03-15")
                .activeNdcCycle("EU Joint NDC (2035 Horizon)")
                .previousSubmissionsSummary("Initial EU NDC submitted 2020-12-17 (-55% by 2030). Domestic policy governed by " + nationalPolicyName + ".")
                .unfcccRegistryUrl("https://unfccc.int/NDCREG")
                .documentLanguage("English / Official EU Languages")
                .longTermGoal(longTerm)
                .targetYearPrimary(2035)
                .policyReviewStatus("JOINT_EU_FULFILMENT")
                .treatyParticipationStatus("PARTY_JOINT_EU")
                .verificationDate("2026-10-28")
                .targets(targets)
                .generalCaveats(List.of(
                        "EU Member States participate via a joint fulfilment agreement under Paris Agreement Article 4(16).",
                        "The -55% (2030) and 66.25–72.5% (2035) commitments are EU-wide joint targets, not identical individual national NDC caps."
                ))
                .provenance(prov)
                .build());
    }
}
