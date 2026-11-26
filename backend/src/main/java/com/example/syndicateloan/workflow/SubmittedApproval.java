package com.example.syndicateloan.workflow;

import java.util.List;

/**
 * UC-024 step 10: 申請した稟議と承認経路。
 *
 * @param requestId 稟議
 * @param status    状態(合議中か承認中)
 * @param steps     承認経路の段
 */
public record SubmittedApproval(long requestId, ApprovalStatus status, List<Step> steps) {

    public SubmittedApproval {
        steps = List.copyOf(steps);
    }

    /**
     * 承認経路の一つの段。
     *
     * @param kind         段の種類
     * @param order        メイントラックの中の順番。メイントラック以外は null
     * @param userId       利用者
     * @param autoAssigned 規則の案のまま変えていないか
     */
    public record Step(StepKind kind, Integer order, long userId, boolean autoAssigned) {
    }
}
