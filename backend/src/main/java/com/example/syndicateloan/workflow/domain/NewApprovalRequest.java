package com.example.syndicateloan.workflow.domain;

import com.example.syndicateloan.workflow.ApprovalStatus;
import com.example.syndicateloan.workflow.ApprovalSubject;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 申請して登録する稟議(UC-024 step 8)。
 *
 * @param organizationId 稟議を回す利用組織
 * @param subject        稟議の種類、対象、経路属性、条件
 * @param title          件名
 * @param body           本文。なければ null
 * @param drafterId      起案者
 * @param routeRuleId    経路の案を出した規則
 * @param status         状態(合議中か承認中)
 * @param submittedAt    申請の日時
 * @param steps          承認経路
 */
public record NewApprovalRequest(
        long organizationId,
        ApprovalSubject subject,
        String title,
        String body,
        long drafterId,
        long routeRuleId,
        ApprovalStatus status,
        LocalDateTime submittedAt,
        List<RouteStep> steps) {

    public NewApprovalRequest {
        steps = List.copyOf(steps);
    }

    /** 申請の回数。最初の申請は1。 */
    public int round() {
        return 1;
    }
}
