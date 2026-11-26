package com.example.syndicateloan.notification.application;

import com.example.syndicateloan.notification.domain.NewNotice;
import com.example.syndicateloan.notification.domain.NoticeKind;
import com.example.syndicateloan.notification.domain.NoticeRepository;
import com.example.syndicateloan.workflow.ApprovalCompleted;
import com.example.syndicateloan.workflow.ApprovalRequested;
import com.example.syndicateloan.workflow.StepKind;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * workflow の公開イベントを受けて、お知らせを届ける(設計方針の「ワークフローの独立」、#41)。
 *
 * <p>申請と同じトランザクションで記録するので、稟議が登録されなければお知らせも残らない
 * (UC-024 の失敗時の事後条件)。
 */
@Component
public class ApprovalNoticeListener {

    private final NoticeRepository notices;
    private final Clock clock;

    public ApprovalNoticeListener(NoticeRepository notices, Clock clock) {
        this.notices = notices;
        this.clock = clock;
    }

    /** UC-024 step 9: 合議先の全員か、メイントラックの最初の承認者に、判断の依頼を届ける。 */
    @EventListener
    public void on(ApprovalRequested event) {
        boolean consultation = event.requestedKind() == StepKind.CONSULTATION;
        NoticeKind kind = consultation ? NoticeKind.CONSULTATION_REQUESTED : NoticeKind.APPROVAL_REQUESTED;
        String message = "稟議「" + event.title() + "」の" + (consultation ? "合議" : "承認") + "の依頼が届きました。";
        LocalDateTime now = LocalDateTime.now(clock);
        event.recipientIds().forEach(recipient -> notices.insert(
                new NewNotice(recipient, kind, "approval-request:" + event.requestId(), message, now)));
    }

    /**
     * UC-025 BR-009・UC-026 BR-009: 差し戻しと否決は起案者に、最終決裁の承認は報告先の全員と起案者に届ける。
     */
    @EventListener
    public void on(ApprovalCompleted event) {
        LocalDateTime now = LocalDateTime.now(clock);
        String ref = "approval-request:" + event.requestId();
        String title = "稟議「" + event.title() + "」";
        switch (event.outcome()) {
            case SENT_BACK -> notices.insert(new NewNotice(event.drafterId(), NoticeKind.SENT_BACK, ref,
                    title + "が差し戻されました。", now));
            case REJECTED -> notices.insert(new NewNotice(event.drafterId(), NoticeKind.REJECTED, ref,
                    title + "が否決されました。", now));
            case APPROVED -> {
                event.reportRecipientIds().forEach(recipient -> notices.insert(new NewNotice(recipient,
                        NoticeKind.REPORTED, ref, title + "が最終決裁で承認されました。", now)));
                notices.insert(new NewNotice(event.drafterId(), NoticeKind.REPORTED, ref,
                        title + "が最終決裁で承認されました。", now));
            }
        }
    }
}
