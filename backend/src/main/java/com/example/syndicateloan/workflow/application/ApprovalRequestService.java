package com.example.syndicateloan.workflow.application;

import com.example.syndicateloan.organization.ActingUser;
import com.example.syndicateloan.organization.Member;
import com.example.syndicateloan.organization.Members;
import com.example.syndicateloan.workflow.ApprovalConcurrentChangeException;
import com.example.syndicateloan.workflow.ApprovalDraft;
import com.example.syndicateloan.workflow.ApprovalDraft.StepAssignment;
import com.example.syndicateloan.workflow.ApprovalForbiddenException;
import com.example.syndicateloan.workflow.ApprovalNotAllowedException;
import com.example.syndicateloan.workflow.ApprovalNotAllowedException.Reason;
import com.example.syndicateloan.workflow.ApprovalRequested;
import com.example.syndicateloan.workflow.ApprovalRequests;
import com.example.syndicateloan.workflow.ApprovalStatus;
import com.example.syndicateloan.workflow.ApprovalSubject;
import com.example.syndicateloan.workflow.ProposedRoute;
import com.example.syndicateloan.workflow.StepKind;
import com.example.syndicateloan.workflow.SubmittedApproval;
import com.example.syndicateloan.workflow.domain.ApprovalRequestRepository;
import com.example.syndicateloan.workflow.domain.NewApprovalRequest;
import com.example.syndicateloan.workflow.domain.RouteRule;
import com.example.syndicateloan.workflow.domain.RouteRuleRepository;
import com.example.syndicateloan.workflow.domain.RoutePlanner;
import com.example.syndicateloan.workflow.domain.RouteStep;
import com.example.syndicateloan.workflow.domain.RuleStep;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC-024 稟議を起案する: step 1〜10 と A1〜A6(A7 は呼び出し側が何も呼ばないことで満たす)。
 * 業務のユースケースが、稟議の中身(種類・対象・経路属性・条件・件名の案)を決めて呼ぶ(#41)。
 */
@Service
@Transactional(readOnly = true)
public class ApprovalRequestService implements ApprovalRequests {

    private final RouteRuleRepository rules;
    private final ApprovalRequestRepository requests;
    private final Members members;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public ApprovalRequestService(RouteRuleRepository rules, ApprovalRequestRepository requests, Members members,
            ApplicationEventPublisher events, Clock clock) {
        this.rules = rules;
        this.requests = requests;
        this.members = members;
        this.events = events;
        this.clock = clock;
    }

    /** UC-024 step 1〜4: 起案を始め、承認経路の案と件名の案を示す。 */
    @Override
    public ProposedRoute propose(ApprovalSubject subject, ActingUser drafter) {
        checkDrafter(drafter);
        checkSubject(subject, drafter);
        RouteRule rule = chooseRule(rules.findBySubjectType(drafter.organizationId(), subject.subjectType()), subject);
        RoutePlanner planner = planner(rule, drafter);
        return new ProposedRoute(rule.id(), rule.version(), subject.titleProposal(),
                planner.propose(members.activeMembers(drafter.organizationId())));
    }

    /** UC-024 step 6〜9: 稟議を申請し、判断の依頼を届ける。 */
    @Override
    @Transactional
    public SubmittedApproval submit(ApprovalDraft draft, ActingUser drafter) {
        ApprovalSubject subject = draft.subject();
        checkDrafter(drafter);
        checkSubject(subject, drafter);
        // UC-024 A6: the rule shown at step 4 was passed by propose, so no matching rule now means
        // another operation changed the rules while the request was drafted.
        RouteRule rule = RouteRule.choose(rules.lockBySubjectType(drafter.organizationId(), subject.subjectType()),
                        subject.attributes())
                .orElseThrow(() -> new ApprovalConcurrentChangeException(
                        "承認経路の規則が、別の操作で変わりました。起案をやり直してください。"));
        // UC-024 A6: the rule chosen at step 3 changed (or another one now applies); register nothing.
        if (rule.id() != draft.routeRuleId() || rule.version() != draft.routeRuleVersion()) {
            throw new ApprovalConcurrentChangeException("承認経路の規則が、別の操作で変わりました。起案をやり直してください。");
        }
        Set<Long> userIds = new HashSet<>();
        draft.steps().stream().map(StepAssignment::userId).filter(Objects::nonNull).forEach(userIds::add);
        List<RouteStep> steps = planner(rule, drafter).validate(draft.title(), draft.steps(),
                members.findAll(userIds));

        // UC-024 BR-008: in consultation when there are consultees, otherwise in approval.
        boolean consulted = steps.stream().anyMatch(step -> step.kind() == StepKind.CONSULTATION);
        ApprovalStatus status = consulted ? ApprovalStatus.IN_CONSULTATION : ApprovalStatus.IN_APPROVAL;
        // UC-024 BR-007: subject, attributes and conditions are recorded as the business use case set them.
        // UC-024 BR-010: record who submitted the request and when.
        long id = requests.insert(new NewApprovalRequest(drafter.organizationId(), subject, draft.title().strip(),
                draft.body(), drafter.userId(), rule.id(), status, LocalDateTime.now(clock), steps));

        // UC-024 BR-008: every consultee in parallel, or else the first approver; reports wait.
        List<Long> recipients = consulted
                ? steps.stream().filter(s -> s.kind() == StepKind.CONSULTATION).map(RouteStep::actorId).toList()
                : steps.stream().filter(s -> s.kind() == StepKind.MAIN).limit(1).map(RouteStep::actorId).toList();
        events.publishEvent(new ApprovalRequested(id, drafter.organizationId(), subject.subjectType(),
                subject.subjectRef(), draft.title().strip(), consulted ? StepKind.CONSULTATION : StepKind.MAIN,
                recipients));

        return new SubmittedApproval(id, status, steps.stream()
                .map(s -> new SubmittedApproval.Step(s.kind(), s.order(), s.actorId(), s.autoAssigned()))
                .toList());
    }

    // UC-024 BR-009: one request per subject in progress or sent back; after a rejection, redraft.
    private void checkSubject(ApprovalSubject subject, ActingUser drafter) {
        requests.findLatest(drafter.organizationId(), subject.subjectType(), subject.subjectRef())
                .ifPresent(latest -> {
                    switch (latest.status()) {
                        case IN_CONSULTATION, IN_APPROVAL -> throw new ApprovalNotAllowedException(
                                Reason.IN_PROGRESS, latest.id(), "同じ対象の稟議が進んでいるため、新しく起案できません。");
                        case DRAFT -> throw new ApprovalNotAllowedException(Reason.SENT_BACK, latest.id(),
                                "同じ対象の稟議が差し戻されています。その稟議を直して再申請してください。");
                        case REJECTED -> throw new ApprovalNotAllowedException(Reason.REJECTED, latest.id(),
                                "同じ対象の稟議が否決されています。条件を変えた再起案として起案してください。");
                        case APPROVED -> {
                            // An approved request does not stop a new one; the business use case decides.
                        }
                    }
                });
    }

    // UC-024 precondition: only a business user drafts a request (whichever business use case calls).
    private static void checkDrafter(ActingUser drafter) {
        if (!drafter.hasRole(ActingUser.BUSINESS_USER)) {
            throw new ApprovalForbiddenException("稟議の起案は、業務ユーザーだけができます。");
        }
    }

    private static RouteRule chooseRule(List<RouteRule> candidates, ApprovalSubject subject) {
        return RouteRule.choose(candidates, subject.attributes())
                // UC-024 A3: no rule matches; the organization admin has to set one up.
                .orElseThrow(() -> new ApprovalNotAllowedException(Reason.NO_ROUTE_RULE, null,
                        "合う承認経路の規則がないため、起案できません。組織管理者に規則の設定を依頼してください。"));
    }

    private RoutePlanner planner(RouteRule rule, ActingUser drafter) {
        Member self = members.findAll(List.of(drafter.userId())).get(drafter.userId());
        Set<Long> positionIds = new HashSet<>();
        rule.steps().stream().map(RuleStep::positionId).filter(Objects::nonNull).forEach(positionIds::add);
        return new RoutePlanner(rule, self, members.positions(positionIds));
    }
}
