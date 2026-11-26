package com.example.syndicateloan.workflow;

import java.util.List;

/**
 * 公開イベント: 稟議が申請され、判断の依頼が届いた(UC-024 step 9)。お知らせはこれを受けて届ける。
 *
 * @param requestId      稟議
 * @param organizationId 稟議を回す利用組織
 * @param subjectType    稟議の種類
 * @param subjectRef     稟議の対象
 * @param title          件名
 * @param requestedKind  判断を依頼した段の種類(合議先か、メイントラックの最初の承認者)
 * @param recipientIds   判断を依頼した利用者
 */
public record ApprovalRequested(
        long requestId,
        long organizationId,
        String subjectType,
        String subjectRef,
        String title,
        StepKind requestedKind,
        List<Long> recipientIds) {

    public ApprovalRequested {
        recipientIds = List.copyOf(recipientIds);
    }
}
