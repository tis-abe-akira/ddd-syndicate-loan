package com.example.syndicateloan.workflow.application;

import com.example.syndicateloan.organization.ActingUser;
import com.example.syndicateloan.organization.Member;
import com.example.syndicateloan.organization.Members;
import com.example.syndicateloan.organization.Position;
import com.example.syndicateloan.workflow.ApprovalCompleted;
import com.example.syndicateloan.workflow.ApprovalConcurrentChangeException;
import com.example.syndicateloan.workflow.ApprovalDecisions;
import com.example.syndicateloan.workflow.ApprovalForbiddenException;
import com.example.syndicateloan.workflow.ApprovalNotFoundException;
import com.example.syndicateloan.workflow.ApprovalRequested;
import com.example.syndicateloan.workflow.ApprovalView;
import com.example.syndicateloan.workflow.AssignedApproval;
import com.example.syndicateloan.workflow.DecisionCommand;
import com.example.syndicateloan.workflow.DecisionNotAllowedException;
import com.example.syndicateloan.workflow.DecisionNotAllowedException.Reason;
import com.example.syndicateloan.workflow.domain.ApprovalRequestRepository;
import com.example.syndicateloan.workflow.domain.ApprovalRequestState;
import com.example.syndicateloan.workflow.domain.DecisionResult;
import com.example.syndicateloan.workflow.domain.StepState;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC-025 稟議に合議する、UC-026 稟議を承認する: step 1〜8 と A1〜A7、UC-026 A8。
 *
 * <p>結果(承認・差し戻し・否決)は公開イベント {@link ApprovalCompleted} で業務に伝える。業務の処理は同じ
 * トランザクションで動くので、業務が処理できずに例外を投げれば、判断ごと記録されない(UC-026 A8)。
 */
@Service
@Transactional(readOnly = true)
public class ApprovalDecisionService implements ApprovalDecisions {

    private final ApprovalRequestRepository requests;
    private final Members members;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public ApprovalDecisionService(ApprovalRequestRepository requests, Members members,
            ApplicationEventPublisher events, Clock clock) {
        this.requests = requests;
        this.members = members;
        this.events = events;
        this.clock = clock;
    }

    @Override
    public List<AssignedApproval> assigned(ActingUser user) {
        requireBusinessUser(user);
        return requests.findAssigned(user.userId());
    }

    @Override
    public ApprovalView view(long requestId, ActingUser user) {
        requireBusinessUser(user);
        return toView(visible(requests.find(requestId, false), requestId, user), user);
    }

    @Override
    @Transactional
    public ApprovalView decide(long requestId, DecisionCommand command, ActingUser user) {
        requireBusinessUser(user);
        // The row stays locked until commit, so decisions on one request are serialized (UC-025 BR-002).
        ApprovalRequestState state = visible(requests.find(requestId, true), requestId, user);
        Optional<Reason> blocked = state.blockedReason(user.userId());
        if (blocked.isPresent()) {
            if (state.version() != command.viewedVersion()) {
                // A6: another operation changed the request after it was opened.
                throw new ApprovalConcurrentChangeException("稟議が、別の操作で変わりました。開き直してください。");
            }
            throw notAllowed(blocked.get());
        }
        DecisionResult result = state.decide(user.userId(), command.decision(), command.comment(),
                LocalDateTime.now(clock));
        // UC-025 BR-010: who decided what, with which comment and when.
        requests.apply(state.id(), state.version(), result);
        if (!result.requestedUserIds().isEmpty()) {
            events.publishEvent(new ApprovalRequested(state.id(), state.organizationId(), state.subjectType(),
                    state.subjectRef(), state.title(), result.requestedKind(), result.requestedUserIds()));
        }
        if (result.outcome() != null) {
            // UC-025 BR-008 / UC-026 BR-008: the business decides what the outcome means for it.
            events.publishEvent(new ApprovalCompleted(state.id(), state.organizationId(), state.subjectType(),
                    state.subjectRef(), state.title(), result.outcome(), state.drafterId(),
                    result.reportRecipientIds()));
        }
        return toView(requests.find(state.id(), false).orElseThrow(), user);
    }

    // precondition of UC-025 and UC-026: only a business user opens and decides requests.
    private static void requireBusinessUser(ActingUser user) {
        if (!user.hasRole(ActingUser.BUSINESS_USER)) {
            throw new ApprovalForbiddenException("稟議を開いて判断できるのは、業務ユーザーだけです。");
        }
    }

    private static ApprovalRequestState visible(Optional<ApprovalRequestState> found, long requestId, ActingUser user) {
        // A1 / NFR-007: another organization's request does not even exist for the user.
        return found.filter(state -> user.belongsTo(state.organizationId()))
                .orElseThrow(() -> new ApprovalNotFoundException(requestId));
    }

    private static DecisionNotAllowedException notAllowed(Reason reason) {
        return new DecisionNotAllowedException(reason, switch (reason) {
            case NOT_IN_PROGRESS -> "稟議が合議中でも承認中でもないため、判断できません。";
            case NOT_ASSIGNED -> "この稟議の、今の回の判断する段にいないため、判断できません。";
            case NOT_YOUR_TURN -> "前の段の判断がまだ終わっていないため、今は判断できません。";
            case ALREADY_DECIDED -> "この稟議の自分の段は、すでに判断しています。";
        });
    }

    private ApprovalView toView(ApprovalRequestState state, ActingUser user) {
        Set<Long> userIds = new HashSet<>();
        userIds.add(state.drafterId());
        state.steps().forEach(s -> userIds.add(s.actorId()));
        Map<Long, Member> names = members.findAll(userIds);
        Set<Long> positionIds = new HashSet<>();
        state.steps().stream().map(StepState::positionId).filter(Objects::nonNull).forEach(positionIds::add);
        Map<Long, Position> positions = members.positions(positionIds);
        return new ApprovalView(state.id(), state.title(), state.body(), state.status(), state.round(),
                state.version(), state.drafterId(), nameOf(names, state.drafterId()), state.subjectType(),
                state.subjectRef(), state.attributes(), state.conditions(),
                state.steps().stream()
                        .map(s -> new ApprovalView.Step(s.kind(), s.order(),
                                s.positionId() == null || positions.get(s.positionId()) == null ? null
                                        : positions.get(s.positionId()).name(),
                                s.actorId(), nameOf(names, s.actorId()), s.decision().name(), s.comment(),
                                s.decidedAt()))
                        .toList(),
                state.blockedReason(user.userId()).orElse(null));
    }

    private static String nameOf(Map<Long, Member> members, long userId) {
        Member member = members.get(userId);
        return member == null ? "" : member.name();
    }
}
