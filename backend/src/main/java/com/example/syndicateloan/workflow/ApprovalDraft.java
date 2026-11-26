package com.example.syndicateloan.workflow;

import java.util.List;
import java.util.Objects;

/**
 * UC-024 step 5、6: 起案者が申請する稟議。
 *
 * @param subject          業務のユースケースが決めた稟議の中身
 * @param routeRuleId      経路の案を出した規則(step 4 で示したもの)
 * @param routeRuleVersion 規則のバージョン(step 4 で示したもの)
 * @param title            件名
 * @param body             本文。なければ null
 * @param steps            起案者が直した承認経路
 */
public record ApprovalDraft(
        ApprovalSubject subject,
        long routeRuleId,
        int routeRuleVersion,
        String title,
        String body,
        List<StepAssignment> steps) {

    public ApprovalDraft {
        Objects.requireNonNull(subject, "subject");
        steps = List.copyOf(steps);
    }

    /**
     * 承認経路の一つの段に入れた利用者。
     *
     * @param kind   段の種類
     * @param order  メイントラックの中の順番。メイントラック以外は null
     * @param userId 利用者。空なら null
     */
    public record StepAssignment(StepKind kind, Integer order, Long userId) {
    }
}
