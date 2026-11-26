package com.example.syndicateloan.workflow.domain;

import com.example.syndicateloan.workflow.ApprovalStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 再申請で記録する新しい回(UC-028 step 8)。
 *
 * @param round       新しい回(前の回 + 1)
 * @param title       件名
 * @param body        本文。なければ null
 * @param routeRuleId 経路の案を出した規則
 * @param status      状態(合議中か承認中)
 * @param submittedAt 再申請の日時
 * @param attributes  直した後の経路属性
 * @param conditions  直した後の稟議の条件
 * @param steps       新しい回の承認経路
 */
public record ResubmittedRound(
        int round,
        String title,
        String body,
        long routeRuleId,
        ApprovalStatus status,
        LocalDateTime submittedAt,
        Map<String, BigDecimal> attributes,
        Map<String, String> conditions,
        List<RouteStep> steps) {

    public ResubmittedRound {
        attributes = Map.copyOf(attributes);
        conditions = Map.copyOf(conditions);
        steps = List.copyOf(steps);
    }
}
