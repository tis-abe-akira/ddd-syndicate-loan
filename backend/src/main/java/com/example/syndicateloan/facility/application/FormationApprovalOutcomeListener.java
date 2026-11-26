package com.example.syndicateloan.facility.application;

import com.example.syndicateloan.facility.domain.Facility;
import com.example.syndicateloan.facility.domain.FacilityRepository;
import com.example.syndicateloan.notification.Notices;
import com.example.syndicateloan.organization.Members;
import com.example.syndicateloan.shared.BusinessRuleException;
import com.example.syndicateloan.syndicate.DealForFormation;
import com.example.syndicateloan.syndicate.DealStatus;
import com.example.syndicateloan.syndicate.Deals;
import com.example.syndicateloan.workflow.ApprovalCompleted;
import java.util.Optional;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 組成の稟議の結果を受けて、ファシリティと融資案件を変える(UC-015 BR-006、BR-010)。
 *
 * <p>稟議の判断と同じトランザクションで動く。最終決裁の承認を処理できなければ例外を投げ、判断ごと取り消す(UC-026 A8)。
 */
@Component
public class FormationApprovalOutcomeListener {

    private final FacilityRepository facilities;
    private final Deals deals;
    private final Members members;
    private final Notices notices;

    public FormationApprovalOutcomeListener(
            FacilityRepository facilities, Deals deals, Members members, Notices notices) {
        this.facilities = facilities;
        this.deals = deals;
        this.members = members;
        this.notices = notices;
    }

    @EventListener
    public void on(ApprovalCompleted event) {
        if (!FacilityApprovalService.SUBJECT_TYPE.equals(event.subjectType())) {
            return;
        }
        switch (event.outcome()) {
            case APPROVED -> {
                Facility facility = facilityUnder(event)
                        .orElseThrow(() -> new BusinessRuleException(
                                "組成の稟議にかかっているファシリティが見つからないため、結果を反映できません。"));
                // UC-026 A8: only the final approval can fail to be taken by the business.
                DealForFormation deal = formingDeal(facility)
                        .orElseThrow(() -> new BusinessRuleException(
                                "融資案件が組成中でないため、組成の稟議の結果を反映できません。"));
                // UC-015 BR-006: the final approval fixes the facility; BR-010: the deal is formed.
                facilities.update(facility.fix());
                deals.completeFormation(deal.id());
                notifyInvestors(facility, deal);
            }
            case SENT_BACK, REJECTED ->
                // UC-015 BR-006 / BR-010: the facility stays under formation and the deal is preparing
                // again. UC-026 A3 / A4 have no failure path, so what is already undone is left as it is.
                facilityUnder(event).ifPresent(facility -> {
                    facilities.update(facility.releasedFromApproval());
                    formingDeal(facility).ifPresent(deal -> deals.returnToPreparing(deal.id()));
                });
        }
    }

    private Optional<DealForFormation> formingDeal(Facility facility) {
        return deals.lockForFormation(facility.dealId()).filter(d -> d.status() == DealStatus.FORMING);
    }

    /** The facility the request is about, while it is still under this request. */
    private Optional<Facility> facilityUnder(ApprovalCompleted event) {
        return parseFacilityId(event.subjectRef()).flatMap(facilities::findById)
                .filter(f -> f.approvalRequestId() != null && f.approvalRequestId() == event.requestId());
    }

    // UC-015 BR-006: each investor learns the share the formation decided.
    private void notifyInvestors(Facility facility, DealForFormation deal) {
        facility.sharePie().shares().forEach((organizationId, amount) -> {
            if (organizationId == deal.leadOrganizationId()) {
                return;
            }
            String message = "ファシリティの組成が確定しました。貴社の持分は "
                    + String.format("%,d", amount.longValueExact()) + " 円です。";
            members.activeMembers(organizationId).forEach(member ->
                    notices.send(member.userId(), "facility:" + facility.id(), message));
        });
    }

    private static Optional<Long> parseFacilityId(String subjectRef) {
        if (subjectRef == null || !subjectRef.startsWith("facility:")) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(subjectRef.substring("facility:".length())));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
