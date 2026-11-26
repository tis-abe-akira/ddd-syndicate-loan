package com.example.syndicateloan.workflow.domain;

import com.example.syndicateloan.workflow.StepKind;

/**
 * 承認経路の規則が出す、一つの段の案。
 *
 * @param kind          段の種類
 * @param order         メイントラックの中の順番。メイントラック以外は null
 * @param positionId    この段に求める職位。なければ null
 * @param defaultUserId 案として自動で入れる利用者。なければ null
 */
public record RuleStep(StepKind kind, Integer order, Long positionId, Long defaultUserId) {
}
