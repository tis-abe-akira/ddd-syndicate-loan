package com.example.syndicateloan.facility.web;

import com.example.syndicateloan.workflow.SubmittedApproval;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * UC-015 step 8: 申請した組成の稟議。融資案件は組成中になり、ファシリティは稟議にかかっている。
 *
 * @param requestId 稟議
 * @param status    稟議の状態(IN_CONSULTATION か IN_APPROVAL)
 * @param steps     承認経路
 */
public record FormationApprovalResponse(long requestId, String status, List<SubmittedStepResponse> steps) {

    static FormationApprovalResponse of(SubmittedApproval submitted) {
        return new FormationApprovalResponse(submitted.requestId(), submitted.status().name(),
                submitted.steps().stream()
                        .map(s -> new SubmittedStepResponse(s.kind().name(), s.order(), s.userId(), s.autoAssigned()))
                        .toList());
    }

    /**
     * 承認経路の一つの段。
     *
     * @param kind         CONSULTATION、MAIN、REPORT
     * @param order        メイントラックの中の順番。メイントラック以外は null
     * @param userId       利用者
     * @param autoAssigned 規則の案のまま変えていないか
     */
    public record SubmittedStepResponse(
            String kind, @Schema(types = {"integer", "null"}) Integer order, long userId, boolean autoAssigned) {
    }
}
