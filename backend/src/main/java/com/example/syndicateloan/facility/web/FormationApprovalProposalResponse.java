package com.example.syndicateloan.facility.web;

import com.example.syndicateloan.facility.application.FormationApprovalProposal;
import com.example.syndicateloan.workflow.ProposedRoute;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * UC-015 step 7: 組成の稟議の承認経路の案。
 *
 * @param facilityId       稟議にかけるファシリティ
 * @param facilityVersion  申請のときに送り返すファシリティのバージョン
 * @param routeRuleId      経路の案を出した規則
 * @param routeRuleVersion 申請のときに送り返す規則のバージョン
 * @param title            件名の案
 * @param steps            承認経路の段(合議先、メイントラックの順、報告先)
 */
public record FormationApprovalProposalResponse(
        long facilityId,
        int facilityVersion,
        long routeRuleId,
        int routeRuleVersion,
        String title,
        List<ProposedStepResponse> steps) {

    static FormationApprovalProposalResponse of(FormationApprovalProposal proposal) {
        ProposedRoute route = proposal.route();
        return new FormationApprovalProposalResponse(proposal.facilityId(), proposal.facilityVersion(),
                route.routeRuleId(), route.routeRuleVersion(), route.title(),
                route.steps().stream().map(s -> new ProposedStepResponse(s.kind().name(), s.order(), s.positionName(),
                        s.userId(), s.candidates().stream()
                                .map(c -> new ApprovalCandidateResponse(c.userId(), c.name(), c.positionName()))
                                .toList()))
                        .toList());
    }

    /**
     * 承認経路の一つの段。
     *
     * @param kind         CONSULTATION(合議先)、MAIN(メイントラック)、REPORT(報告先)
     * @param order        メイントラックの中の順番。メイントラック以外は null
     * @param positionName この段に求める職位。なければ null
     * @param userId       規則が自動で入れた利用者。決まらなければ null
     * @param candidates   この段に入れられる利用者
     */
    public record ProposedStepResponse(
            String kind,
            @Schema(types = {"integer", "null"}) Integer order,
            @Schema(types = {"string", "null"}) String positionName,
            @Schema(types = {"integer", "null"}) Long userId,
            List<ApprovalCandidateResponse> candidates) {
    }

    /**
     * 段に入れられる利用者。
     *
     * @param userId       利用者
     * @param name         氏名
     * @param positionName 職位。なければ null
     */
    public record ApprovalCandidateResponse(long userId, String name, @Schema(types = {"string", "null"}) String positionName) {
    }
}
