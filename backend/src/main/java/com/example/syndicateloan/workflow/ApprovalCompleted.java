package com.example.syndicateloan.workflow;

import java.util.List;

/**
 * 公開イベント: 稟議が最終決裁で承認されたか、差し戻されたか、否決された(UC-025 BR-008、UC-026 BR-008)。
 * 業務はこれを受けて自分の処理をする。業務が処理できなければ、例外を投げて判断ごと取り消す(UC-026 A8)。
 *
 * @param requestId          稟議
 * @param organizationId     稟議を回す利用組織
 * @param subjectType        稟議の種類
 * @param subjectRef         稟議の対象
 * @param title              件名
 * @param outcome            結果
 * @param drafterId          起案者
 * @param reportRecipientIds 報告先(承認のときだけ)
 */
public record ApprovalCompleted(
        long requestId,
        long organizationId,
        String subjectType,
        String subjectRef,
        String title,
        ApprovalOutcome outcome,
        long drafterId,
        List<Long> reportRecipientIds) {

    public ApprovalCompleted {
        reportRecipientIds = List.copyOf(reportRecipientIds);
    }
}
