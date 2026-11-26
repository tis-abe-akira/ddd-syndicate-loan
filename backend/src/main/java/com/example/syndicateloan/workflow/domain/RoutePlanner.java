package com.example.syndicateloan.workflow.domain;

import com.example.syndicateloan.organization.Member;
import com.example.syndicateloan.organization.Position;
import com.example.syndicateloan.workflow.ApprovalDraft.StepAssignment;
import com.example.syndicateloan.workflow.InvalidApprovalDraftException.DraftError;
import com.example.syndicateloan.workflow.ProposedRoute.Candidate;
import com.example.syndicateloan.workflow.ProposedRoute.ProposedStep;
import com.example.syndicateloan.workflow.StepKind;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 承認経路の規則から承認経路の案を作り(UC-024 step 4)、起案者が直した承認経路を確かめる(step 7)。
 * 利用者と職位は呼び出し側が渡すので、データベースを知らない。
 *
 * @param rule        選んだ承認経路の規則
 * @param drafter     起案者
 * @param positions   規則の段に求める職位(id から)
 */
public record RoutePlanner(RouteRule rule, Member drafter, Map<Long, Position> positions) {

    public RoutePlanner {
        positions = Map.copyOf(positions);
    }

    /** 段の並び: 合議先、メイントラック、報告先。 */
    public List<RuleStep> orderedSteps() {
        List<RuleStep> ordered = new ArrayList<>(rule.steps(StepKind.CONSULTATION));
        ordered.addAll(rule.steps(StepKind.MAIN));
        ordered.addAll(rule.steps(StepKind.REPORT));
        return ordered;
    }

    /** UC-024 step 4、BR-002: 規則の案から作る承認経路。 */
    public List<ProposedStep> propose(List<Member> activeMembers) {
        return orderedSteps().stream()
                .map(step -> new ProposedStep(
                        step.kind(),
                        step.order(),
                        step.positionId(),
                        Optional.ofNullable(step.positionId()).map(positions::get).map(Position::name).orElse(null),
                        // UC-024 BR-002: the rule's default user, or an empty step the drafter fills in (A4).
                        step.defaultUserId(),
                        activeMembers.stream()
                                .filter(member -> isEligible(step, member))
                                .map(member -> new Candidate(member.userId(), member.name(),
                                        member.position() == null ? null : member.position().name()))
                                .toList()))
                .toList();
    }

    /**
     * UC-024 step 7: 起案者が直した承認経路を確かめ、登録する段にする。
     *
     * @param title       件名
     * @param assignments 起案者が直した承認経路
     * @param members     承認経路に入れた利用者(id から)
     * @throws com.example.syndicateloan.workflow.InvalidApprovalDraftException A5
     */
    public List<RouteStep> validate(String title, List<StepAssignment> assignments, Map<Long, Member> members) {
        List<DraftError> errors = new ArrayList<>();
        if (title == null || title.isBlank()) {
            errors.add(new DraftError("title", "件名を入力してください。"));
        }
        List<RuleStep> mainSteps = rule.steps(StepKind.MAIN);
        // UC-024 BR-006: the main track has at least one step.
        if (mainSteps.isEmpty()) {
            errors.add(new DraftError("steps", "承認経路の規則にメイントラックの段がありません。組織管理者に確認してください。"));
        }
        List<Integer> draftedOrders = assignments.stream()
                .filter(a -> a.kind() == StepKind.MAIN)
                .map(StepAssignment::order)
                .toList();
        // UC-024 BR-003: main-track steps cannot be added, removed or reordered.
        if (!draftedOrders.equals(mainSteps.stream().map(RuleStep::order).toList())) {
            errors.add(new DraftError("steps", "メイントラックの段は、足すことも削ることも順番を変えることもできません。"));
        }

        List<RouteStep> steps = new ArrayList<>();
        Set<Long> deciders = new HashSet<>();
        for (int i = 0; i < assignments.size(); i++) {
            StepAssignment assignment = assignments.get(i);
            String field = "steps[" + i + "]";
            Optional<RuleStep> ruleStep = ruleStepFor(assignment);
            if (assignment.kind() == StepKind.MAIN && ruleStep.isEmpty()) {
                continue; // already reported as a changed main track
            }
            if (assignment.userId() == null) {
                errors.add(new DraftError(field, "利用者を選んでください。"));
                continue;
            }
            Member member = members.get(assignment.userId());
            // UC-024 BR-006: route users are active users of the drafter's organization.
            if (member == null || !member.active() || member.organizationId() != drafter.organizationId()) {
                errors.add(new DraftError(field, "同じ利用組織の、有効な利用者を選んでください。"));
                continue;
            }
            // UC-024 BR-004: the drafter is neither a consultee nor an approver of their own request.
            if (assignment.kind() != StepKind.REPORT && member.userId() == drafter.userId()) {
                errors.add(new DraftError(field, "起案者自身は、合議先にも承認者にもなれません。"));
                continue;
            }
            if (assignment.kind() == StepKind.MAIN) {
                String rankError = rankError(ruleStep.get(), member);
                if (rankError != null) {
                    errors.add(new DraftError(field, rankError));
                    continue;
                }
            }
            // UC-024 BR-005: a user decides in one step only (reports may overlap).
            if (assignment.kind() != StepKind.REPORT && !deciders.add(member.userId())) {
                errors.add(new DraftError(field, "同じ利用者を、二つ以上の合議先とメイントラックの段に入れられません。"));
                continue;
            }
            steps.add(new RouteStep(
                    assignment.kind(),
                    assignment.kind() == StepKind.MAIN ? assignment.order() : null,
                    ruleStep.map(RuleStep::positionId).orElse(null),
                    member.userId(),
                    // UC-024 BR-003: steps changed or added by the drafter are recorded as such.
                    ruleStep.map(s -> Objects.equals(s.defaultUserId(), member.userId())).orElse(false)));
        }
        if (!errors.isEmpty()) {
            throw new com.example.syndicateloan.workflow.InvalidApprovalDraftException(errors);
        }
        return steps;
    }

    /** 申請した段に当たる規則の段。メイントラックは順番で、合議先と報告先は既定の利用者で探す。 */
    private Optional<RuleStep> ruleStepFor(StepAssignment assignment) {
        if (assignment.kind() == StepKind.MAIN) {
            return rule.steps(StepKind.MAIN).stream()
                    .filter(step -> Objects.equals(step.order(), assignment.order()))
                    .findFirst();
        }
        return rule.steps(assignment.kind()).stream()
                .filter(step -> step.defaultUserId() != null && step.defaultUserId().equals(assignment.userId()))
                .findFirst();
    }

    /** 規則の段に入れられる利用者か(候補の絞り込み)。 */
    private boolean isEligible(RuleStep step, Member member) {
        if (step.kind() != StepKind.REPORT && member.userId() == drafter.userId()) {
            return false;
        }
        if (step.kind() == StepKind.MAIN) {
            return rankError(step, member) == null;
        }
        Position required = step.positionId() == null ? null : positions.get(step.positionId());
        return required == null || member.rank() >= required.rank();
    }

    // UC-024 BR-003: a main step takes the required position or higher, below the next step's.
    private String rankError(RuleStep step, Member member) {
        Position required = positions.get(step.positionId());
        if (required != null && member.rank() < required.rank()) {
            return "この段には、" + required.name() + "以上の利用者を選んでください。";
        }
        Optional<Position> next = rule.steps(StepKind.MAIN).stream()
                .filter(s -> s.order() > step.order())
                .findFirst()
                .map(s -> positions.get(s.positionId()));
        if (next.isPresent() && member.rank() >= next.get().rank()) {
            return "この段には、" + next.get().name() + "より低い職位の利用者を選んでください。";
        }
        return null;
    }
}
