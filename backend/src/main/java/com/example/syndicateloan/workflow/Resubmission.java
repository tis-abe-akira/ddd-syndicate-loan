package com.example.syndicateloan.workflow;

import com.example.syndicateloan.workflow.ApprovalDraft.StepAssignment;
import java.util.List;
import java.util.Objects;

/**
 * UC-028 step 5、6: 起案者が再申請する稟議。
 *
 * <p>件名の案は業務から受け取らないので(UC-028 BR-005)、{@code subject} の件名の案は使わない。
 *
 * @param subject          業務のユースケースが決めた、直した後の稟議の中身
 * @param requestId        差し戻された稟議(step 2 で示したもの)
 * @param requestVersion   稟議のバージョン(step 2 で示したもの、A6)
 * @param routeRuleId      経路の案を出した規則(step 4 で示したもの)
 * @param routeRuleVersion 規則のバージョン(step 4 で示したもの、A6)
 * @param title            件名
 * @param body             本文。なければ null
 * @param steps            起案者が直した、新しい回の承認経路
 */
public record Resubmission(
        ApprovalSubject subject,
        long requestId,
        int requestVersion,
        long routeRuleId,
        int routeRuleVersion,
        String title,
        String body,
        List<StepAssignment> steps) {

    public Resubmission {
        Objects.requireNonNull(subject, "subject");
        steps = List.copyOf(steps);
    }
}
