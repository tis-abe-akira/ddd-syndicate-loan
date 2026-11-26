package com.example.syndicateloan.workflow.domain;

import com.example.syndicateloan.workflow.ApprovalOutcome;
import com.example.syndicateloan.workflow.ApprovalStatus;
import com.example.syndicateloan.workflow.Decision;
import com.example.syndicateloan.workflow.DecisionNotAllowedException.Reason;
import com.example.syndicateloan.workflow.InvalidApprovalDraftException;
import com.example.syndicateloan.workflow.InvalidApprovalDraftException.DraftError;
import com.example.syndicateloan.workflow.StepKind;
import com.example.syndicateloan.workflow.domain.DecisionResult.StepChange;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 稟議と、今の回の承認経路(UC-025、UC-026)。判断の規則をここに置き、データベースを知らない。
 *
 * @param id             稟議
 * @param organizationId 稟議を回す利用組織
 * @param subjectType    稟議の種類
 * @param subjectRef     稟議の対象
 * @param title          件名
 * @param body           本文。なければ null
 * @param drafterId      起案者
 * @param status         状態
 * @param round          申請の回数
 * @param version        バージョン
 * @param submittedAt    最後に申請した日時
 * @param attributes     経路属性
 * @param conditions     稟議の条件
 * @param steps          今の回の段
 */
public record ApprovalRequestState(
        long id,
        long organizationId,
        String subjectType,
        String subjectRef,
        String title,
        String body,
        long drafterId,
        ApprovalStatus status,
        int round,
        int version,
        LocalDateTime submittedAt,
        Map<String, BigDecimal> attributes,
        Map<String, String> conditions,
        List<StepState> steps) {

    public ApprovalRequestState {
        attributes = Map.copyOf(attributes);
        conditions = Map.copyOf(conditions);
        steps = List.copyOf(steps);
    }

    /** UC-025・UC-026 A2: 利用者が今は判断できない理由。判断できるなら空。 */
    public Optional<Reason> blockedReason(long userId) {
        if (status != ApprovalStatus.IN_CONSULTATION && status != ApprovalStatus.IN_APPROVAL) {
            return Optional.of(Reason.NOT_IN_PROGRESS);
        }
        List<StepState> mine = steps.stream()
                .filter(s -> s.kind() != StepKind.REPORT && s.actorId() == userId)
                .toList();
        if (mine.isEmpty()) {
            return Optional.of(Reason.NOT_ASSIGNED);
        }
        // UC-025 BR-006: a step is decided only once.
        if (mine.stream().allMatch(s -> s.decision() != StepDecision.PENDING)) {
            return Optional.of(Reason.ALREADY_DECIDED);
        }
        return actionableStep(userId).isPresent() ? Optional.empty() : Optional.of(Reason.NOT_YOUR_TURN);
    }

    /**
     * UC-025・UC-026 step 5〜7: 判断を確かめ、稟議がどう変わるかを決める。
     *
     * @throws InvalidApprovalDraftException  A5(差し戻しと否決のコメントがない)
     * @throws IllegalStateException          利用者が今は判断できないとき(先に {@link #blockedReason} を確かめる)
     */
    public DecisionResult decide(long userId, Decision decision, String comment, LocalDateTime now) {
        StepState step = actionableStep(userId).orElseThrow(() -> new IllegalStateException("Cannot decide now"));
        String text = comment == null || comment.isBlank() ? null : comment.strip();
        // UC-025 BR-005: sending back and rejecting need a comment; approving does not.
        if (decision != Decision.APPROVE && text == null) {
            throw new InvalidApprovalDraftException(
                    List.of(new DraftError("comment", "差し戻しと否決には、理由のコメントを入力してください。")));
        }
        List<StepChange> changes = new ArrayList<>();
        return switch (decision) {
            case APPROVE -> approve(step, text, now, changes);
            case SEND_BACK -> close(step, StepDecision.SENT_BACK, ApprovalStatus.DRAFT, null, ApprovalOutcome.SENT_BACK,
                    text, now, changes);
            case REJECT -> close(step, StepDecision.REJECTED, ApprovalStatus.REJECTED, now, ApprovalOutcome.REJECTED,
                    text, now, changes);
        };
    }

    private DecisionResult approve(StepState step, String comment, LocalDateTime now, List<StepChange> changes) {
        changes.add(new StepChange(step.id(), StepDecision.APPROVED, comment, now));
        if (step.kind() == StepKind.CONSULTATION) {
            boolean othersPending = pending(StepKind.CONSULTATION).stream().anyMatch(s -> s.id() != step.id());
            if (othersPending) {
                return new DecisionResult(status, null, changes, null, List.of(), null, List.of());
            }
            // UC-025 BR-002: all consultees approved; the request moves on to the main track.
            List<Long> first = pending(StepKind.MAIN).stream().limit(1).map(StepState::actorId).toList();
            return new DecisionResult(ApprovalStatus.IN_APPROVAL, null, changes, StepKind.MAIN, first, null, List.of());
        }
        Optional<StepState> next = pending(StepKind.MAIN).stream().filter(s -> s.id() != step.id()).findFirst();
        if (next.isPresent()) {
            // UC-026 BR-009: a non-final approval asks the next approver.
            return new DecisionResult(status, null, changes, StepKind.MAIN, List.of(next.get().actorId()), null,
                    List.of());
        }
        // UC-026 BR-002: the final approval approves the request and reports it.
        List<Long> reports = new ArrayList<>();
        for (StepState report : pending(StepKind.REPORT)) {
            changes.add(new StepChange(report.id(), StepDecision.NOTIFIED, null, now));
            reports.add(report.actorId());
        }
        return new DecisionResult(ApprovalStatus.APPROVED, now, changes, null, List.of(), ApprovalOutcome.APPROVED,
                reports);
    }

    // UC-025 BR-003 / BR-004: sending back or rejecting skips this round's undecided steps.
    private DecisionResult close(StepState step, StepDecision decision, ApprovalStatus newStatus,
            LocalDateTime decidedAt, ApprovalOutcome outcome, String comment, LocalDateTime now,
            List<StepChange> changes) {
        changes.add(new StepChange(step.id(), decision, comment, now));
        steps.stream()
                .filter(s -> s.kind() != StepKind.REPORT && s.decision() == StepDecision.PENDING)
                .filter(s -> s.id() != step.id())
                .forEach(s -> changes.add(new StepChange(s.id(), StepDecision.SKIPPED, null, null)));
        return new DecisionResult(newStatus, decidedAt, changes, null, List.of(), outcome, List.of());
    }

    /** 利用者が今判断できる段。 */
    private Optional<StepState> actionableStep(long userId) {
        if (status == ApprovalStatus.IN_CONSULTATION) {
            // UC-025 BR-001: consultees decide in parallel, in any order.
            return pending(StepKind.CONSULTATION).stream().filter(s -> s.actorId() == userId).findFirst();
        }
        if (status == ApprovalStatus.IN_APPROVAL) {
            // UC-026 BR-001 / BR-007: only the owner of the lowest undecided main step decides.
            return pending(StepKind.MAIN).stream().findFirst().filter(s -> s.actorId() == userId);
        }
        return Optional.empty();
    }

    private List<StepState> pending(StepKind kind) {
        return steps.stream()
                .filter(s -> s.kind() == kind && s.decision() == StepDecision.PENDING)
                .sorted(Comparator.comparing(s -> s.order() == null ? 0 : s.order()))
                .toList();
    }
}
