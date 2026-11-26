package com.example.syndicateloan.workflow.domain;

import com.example.syndicateloan.workflow.ApprovalOutcome;
import com.example.syndicateloan.workflow.ApprovalStatus;
import com.example.syndicateloan.workflow.StepKind;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 一つの判断で稟議がどう変わるか。
 *
 * @param status            稟議の新しい状態
 * @param decidedAt         承認済か否決になった日時。それ以外は null
 * @param stepChanges       変わる段
 * @param requestedKind     次に判断を依頼する段の種類。依頼しなければ null
 * @param requestedUserIds  次に判断を依頼する利用者
 * @param outcome           業務に伝える結果。まだ結果が出ていなければ null
 * @param reportRecipientIds 報告先(承認済のときだけ)
 */
public record DecisionResult(
        ApprovalStatus status,
        LocalDateTime decidedAt,
        List<StepChange> stepChanges,
        StepKind requestedKind,
        List<Long> requestedUserIds,
        ApprovalOutcome outcome,
        List<Long> reportRecipientIds) {

    public DecisionResult {
        stepChanges = List.copyOf(stepChanges);
        requestedUserIds = List.copyOf(requestedUserIds);
        reportRecipientIds = List.copyOf(reportRecipientIds);
    }

    /**
     * 一つの段の変化。
     *
     * @param stepId    段
     * @param decision  新しい判断
     * @param comment   コメント。なければ null
     * @param decidedAt 判断した日時。PENDING と SKIPPED では null
     */
    public record StepChange(long stepId, StepDecision decision, String comment, LocalDateTime decidedAt) {
    }
}
