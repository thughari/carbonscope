package com.carbonscope.dto.policy;

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
public class CountryNdcProfileDto {
    private String countryCode;
    private String countryName;
    private String documentTitle;
    private String submissionDate;
    private String latestSubmissionDate;
    private String activeNdcCycle; // e.g., "NDC 3.0 (2035 Horizon)", "NDC 2.0 (2030 Horizon)"
    private String previousSubmissionsSummary;
    private String unfcccRegistryUrl;
    private String documentLanguage;
    private String longTermGoal;
    private Integer targetYearPrimary;
    private List<NdcTargetDto> targets;
    private List<String> generalCaveats;
    private String policyReviewStatus; // VERIFIED_ACTIVE, JOINT_EU_FULFILMENT, WITHDRAWN_PARIS_AGREEMENT, REQUIRES_REVIEW
    private String treatyParticipationStatus; // PARTY_ACTIVE, WITHDRAWN_NON_PARTY, PARTY_JOINT_EU
    private String treatyWithdrawalEffectiveDate; // e.g. "2026-01-27" when withdrawn, otherwise null
    private String verificationDate;
    private ProvenanceDto provenance;
}
