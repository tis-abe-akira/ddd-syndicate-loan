package com.example.syndicateloan.workflow;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

/**
 * 業務のユースケースが決める、稟議の中身(UC-024 の前提条件、BR-007)。起案者は変えられない。
 * 承認ワークフローは、稟議の種類と稟議の対象の意味を解釈しない(NFR-010)。
 *
 * @param subjectType   稟議の種類(例: FACILITY_FORMATION)
 * @param subjectRef    稟議の対象への参照(業務が決める不透明な文字列)
 * @param titleProposal 件名の案。起案者は直せる
 * @param attributes    経路属性(amount、credit_score など)
 * @param conditions    稟議の条件(否決後の再起案で、条件を変えたかを比べる)
 */
public record ApprovalSubject(
        String subjectType,
        String subjectRef,
        String titleProposal,
        Map<String, BigDecimal> attributes,
        Map<String, String> conditions) {

    public ApprovalSubject {
        Objects.requireNonNull(subjectType, "subjectType");
        Objects.requireNonNull(subjectRef, "subjectRef");
        Objects.requireNonNull(titleProposal, "titleProposal");
        attributes = Map.copyOf(attributes);
        conditions = Map.copyOf(conditions);
    }
}
