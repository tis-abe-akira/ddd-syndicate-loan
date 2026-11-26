package com.example.syndicateloan.workflow.application;

import com.example.syndicateloan.organization.ActingUser;
import com.example.syndicateloan.organization.Member;
import com.example.syndicateloan.organization.Members;
import com.example.syndicateloan.workflow.ApprovalConcurrentChangeException;
import com.example.syndicateloan.workflow.ApprovalDecisions;
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
import com.example.syndicateloan.workflow.Resubmission;
import com.example.syndicateloan.workflow.ResubmissionProposal;
import com.example.syndicateloan.workflow.StepKind;
import com.example.syndicateloan.workflow.SubmittedApproval;
import com.example.syndicateloan.workflow.domain.ApprovalRequestRepository;
import com.example.syndicateloan.workflow.domain.ApprovalRequestState;
import com.example.syndicateloan.workflow.domain.LatestRequest;
import com.example.syndicateloan.workflow.domain.ResubmittedRound;
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
import java.util.Optional;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC-024 稟議を起案する: step 1〜10 と A1〜A6(A7 は呼び出し側が何も呼ばないことで満たす)。
 * UC-028 差し戻された稟議を再申請する: step 1〜10 と A1〜A6(A7 も同じ)。
 * 業務のユースケースが、稟議の中身(種類・対象・経路属性・条件・件名の案)を決めて呼ぶ(#41)。
 */
@Service
@Transactional(readOnly = true)
public class ApprovalRequestService implements ApprovalRequests {

    private final RouteRuleRepository rules;
    private final ApprovalRequestRepository requests;
    private final Members members;
    private final ApprovalDecisions decisions;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public ApprovalRequestService(RouteRuleRepository rules, ApprovalRequestRepository requests, Members members,
            ApprovalDecisions decisions, ApplicationEventPublisher events, Clock clock) {
        this.rules = rules;
        this.requests = requests;
        this.members = members;
        this.decisions = decisions;
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
        RouteRule rule = lockRule(subject, drafter, draft.routeRuleId(), draft.routeRuleVersion());
        List<RouteStep> steps = validated(rule, drafter, draft.title(), draft.steps());

        // UC-024 BR-008: in consultation when there are consultees, otherwise in approval.
        ApprovalStatus status = firstStatus(steps);
        // UC-024 BR-007: subject, attributes and conditions are recorded as the business use case set them.
        // UC-024 BR-010: record who submitted the request and when.
        long id = requests.insert(new NewApprovalRequest(drafter.organizationId(), subject, draft.title().strip(),
                draft.body(), drafter.userId(), rule.id(), status, LocalDateTime.now(clock), steps));
        requestFirstDecisions(id, drafter, subject, draft.title().strip(), steps);
        return submitted(id, status, steps);
    }

    /** UC-028 step 1〜4: 差し戻された稟議の再申請を始め、前の回の判断と新しい回の承認経路の案を示す。 */
    @Override
    public ResubmissionProposal proposeResubmission(ApprovalSubject subject, ActingUser drafter) {
        checkDrafter(drafter);
        LatestRequest sentBack = sentBack(subject, drafter);
        RouteRule rule = chooseRule(rules.findBySubjectType(drafter.organizationId(), subject.subjectType()), subject);
        var previous = decisions.view(sentBack.id(), drafter);
        // UC-028 BR-005: the title and body of the previous round are the proposal, not the business's.
        // UC-028 BR-004: the route is planned again from the rule chosen for today's attributes.
        return new ResubmissionProposal(previous, new ProposedRoute(rule.id(), rule.version(), previous.title(),
                planner(rule, drafter).propose(members.activeMembers(drafter.organizationId()))));
    }

    /** UC-028 step 6〜9: 差し戻された稟議を新しい回として再申請し、判断の依頼を届ける。 */
    @Override
    @Transactional
    public SubmittedApproval resubmit(Resubmission resubmission, ActingUser drafter) {
        ApprovalSubject subject = resubmission.subject();
        checkDrafter(drafter);
        LatestRequest sentBack = sentBack(subject, drafter);
        // UC-028 A6: the request shown at step 2 was resubmitted (or otherwise changed) since.
        // UC-028 BR-008: the sent-back request is still the latest one for the subject (UC-024 BR-009).
        ApprovalRequestState state = requests.find(sentBack.id(), true)
                .filter(found -> found.id() == resubmission.requestId())
                .filter(found -> found.version() == resubmission.requestVersion())
                .orElseThrow(() -> new ApprovalConcurrentChangeException(
                        "稟議が、別の操作で変わりました。再申請をやり直してください。"));
        RouteRule rule = lockRule(subject, drafter, resubmission.routeRuleId(), resubmission.routeRuleVersion());
        List<RouteStep> steps = validated(rule, drafter, resubmission.title(), resubmission.steps());

        // UC-028 BR-007 (UC-024 BR-008): in consultation when there are consultees, otherwise in approval.
        ApprovalStatus status = firstStatus(steps);
        String title = resubmission.title().strip();
        // UC-028 BR-003: a new round, decided from its first step again, by everyone in it.
        // UC-028 BR-009: record who resubmitted the request and when.
        requests.resubmit(state.id(), state.version(), new ResubmittedRound(state.round() + 1, title,
                resubmission.body(), rule.id(), status, LocalDateTime.now(clock), subject.attributes(),
                subject.conditions(), steps));
        requestFirstDecisions(state.id(), drafter, subject, title, steps);
        return submitted(state.id(), status, steps);
    }

    // UC-028 BR-001: only a sent-back request is resubmitted; UC-028 BR-002: by its own drafter.
    private LatestRequest sentBack(ApprovalSubject subject, ActingUser drafter) {
        Optional<LatestRequest> latest = requests.findLatest(drafter.organizationId(), subject.subjectType(),
                subject.subjectRef());
        LatestRequest found = latest.filter(l -> l.status() == ApprovalStatus.DRAFT)
                .orElseThrow(() -> latest.filter(l -> l.status() == ApprovalStatus.REJECTED)
                        // UC-028 A1: a rejected request is redrafted with other conditions (UC-029).
                        .map(l -> new ApprovalNotAllowedException(Reason.REJECTED, l.id(),
                                "同じ対象の稟議が否決されています。条件を変えた再起案として起案してください。"))
                        // UC-028 A1: nothing to resubmit.
                        .orElseGet(() -> new ApprovalNotAllowedException(Reason.NOT_SENT_BACK,
                                latest.map(LatestRequest::id).orElse(null), "再申請できる、差し戻された稟議がありません。")));
        long drafterId = requests.find(found.id(), false).orElseThrow().drafterId();
        if (drafterId != drafter.userId()) {
            // UC-028 A2: only the drafter of the sent-back request resubmits it.
            throw new ApprovalNotAllowedException(Reason.NOT_DRAFTER, found.id(),
                    "差し戻された稟議を再申請できるのは、その稟議の起案者だけです。");
        }
        return found;
    }

    private RouteRule lockRule(ApprovalSubject subject, ActingUser drafter, long ruleId, int ruleVersion) {
        // UC-024 A6 / UC-028 A6: the rule shown at step 4 was passed by the proposal, so no matching rule now
        // means another operation changed the rules in between.
        RouteRule rule = RouteRule.choose(rules.lockBySubjectType(drafter.organizationId(), subject.subjectType()),
                        subject.attributes())
                .orElseThrow(() -> new ApprovalConcurrentChangeException(
                        "承認経路の規則が、別の操作で変わりました。やり直してください。"));
        // UC-024 A6 / UC-028 A6: the rule chosen at step 3 changed (or another one now applies).
        if (rule.id() != ruleId || rule.version() != ruleVersion) {
            throw new ApprovalConcurrentChangeException("承認経路の規則が、別の操作で変わりました。やり直してください。");
        }
        return rule;
    }

    // UC-028 BR-004: UC-024 BR-003〜BR-006 apply to the new round as well.
    private List<RouteStep> validated(RouteRule rule, ActingUser drafter, String title, List<StepAssignment> steps) {
        Set<Long> userIds = new HashSet<>();
        steps.stream().map(StepAssignment::userId).filter(Objects::nonNull).forEach(userIds::add);
        return planner(rule, drafter).validate(title, steps, members.findAll(userIds));
    }

    private static ApprovalStatus firstStatus(List<RouteStep> steps) {
        return steps.stream().anyMatch(step -> step.kind() == StepKind.CONSULTATION)
                ? ApprovalStatus.IN_CONSULTATION : ApprovalStatus.IN_APPROVAL;
    }

    // UC-024 BR-008: every consultee in parallel, or else the first approver; reports wait.
    private void requestFirstDecisions(long id, ActingUser drafter, ApprovalSubject subject, String title,
            List<RouteStep> steps) {
        boolean consulted = firstStatus(steps) == ApprovalStatus.IN_CONSULTATION;
        List<Long> recipients = consulted
                ? steps.stream().filter(s -> s.kind() == StepKind.CONSULTATION).map(RouteStep::actorId).toList()
                : steps.stream().filter(s -> s.kind() == StepKind.MAIN).limit(1).map(RouteStep::actorId).toList();
        events.publishEvent(new ApprovalRequested(id, drafter.organizationId(), subject.subjectType(),
                subject.subjectRef(), title, consulted ? StepKind.CONSULTATION : StepKind.MAIN, recipients));
    }

    private static SubmittedApproval submitted(long id, ApprovalStatus status, List<RouteStep> steps) {
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
