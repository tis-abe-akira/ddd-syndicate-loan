package com.example.syndicateloan.workflow.persistence;

import static com.example.syndicateloan.workflow.persistence.jooq.generated.Tables.APPROVAL_ATTRIBUTE;
import static com.example.syndicateloan.workflow.persistence.jooq.generated.Tables.APPROVAL_CONDITION;
import static com.example.syndicateloan.workflow.persistence.jooq.generated.Tables.APPROVAL_REQUEST;
import static com.example.syndicateloan.workflow.persistence.jooq.generated.Tables.APPROVAL_STEP;

import com.example.syndicateloan.workflow.ApprovalConcurrentChangeException;
import com.example.syndicateloan.workflow.ApprovalStatus;
import com.example.syndicateloan.workflow.AssignedApproval;
import com.example.syndicateloan.workflow.StepKind;
import com.example.syndicateloan.workflow.domain.ApprovalRequestState;
import com.example.syndicateloan.workflow.domain.DecisionResult;
import com.example.syndicateloan.workflow.domain.StepDecision;
import com.example.syndicateloan.workflow.domain.StepState;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import com.example.syndicateloan.workflow.domain.ApprovalRequestRepository;
import com.example.syndicateloan.workflow.domain.LatestRequest;
import com.example.syndicateloan.workflow.domain.NewApprovalRequest;
import com.example.syndicateloan.workflow.domain.ResubmittedRound;
import com.example.syndicateloan.workflow.domain.RouteStep;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** {@link ApprovalRequestRepository} の jOOQ のアダプタ。 */
@Repository
@Transactional(readOnly = true)
public class JooqApprovalRequestRepository implements ApprovalRequestRepository {

    private final DSLContext ctx;

    public JooqApprovalRequestRepository(DSLContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public Optional<LatestRequest> findLatest(long organizationId, String subjectType, String subjectRef) {
        return ctx.select(APPROVAL_REQUEST.ID, APPROVAL_REQUEST.STATUS)
                .from(APPROVAL_REQUEST)
                // UC-024 BR-009: requests of the same organization, subject type and subject.
                .where(APPROVAL_REQUEST.ORGANIZATION_ID.eq(organizationId))
                .and(APPROVAL_REQUEST.SUBJECT_TYPE.eq(subjectType))
                .and(APPROVAL_REQUEST.SUBJECT_REF.eq(subjectRef))
                .orderBy(APPROVAL_REQUEST.ID.desc())
                .limit(1)
                .fetchOptional(row -> new LatestRequest(row.value1(), ApprovalStatus.valueOf(row.value2())));
    }

    @Override
    @Transactional
    public long insert(NewApprovalRequest request) {
        long id;
        try {
            id = ctx.insertInto(APPROVAL_REQUEST)
                    .set(APPROVAL_REQUEST.ORGANIZATION_ID, request.organizationId())
                    .set(APPROVAL_REQUEST.SUBJECT_TYPE, request.subject().subjectType())
                    .set(APPROVAL_REQUEST.SUBJECT_REF, request.subject().subjectRef())
                    .set(APPROVAL_REQUEST.TITLE, request.title())
                    .set(APPROVAL_REQUEST.BODY, request.body())
                    .set(APPROVAL_REQUEST.DRAFTER_ID, request.drafterId())
                    .set(APPROVAL_REQUEST.ROUTE_RULE_ID, request.routeRuleId())
                    .set(APPROVAL_REQUEST.STATUS, request.status().name())
                    .set(APPROVAL_REQUEST.ROUND, request.round())
                    .set(APPROVAL_REQUEST.SUBMITTED_AT, request.submittedAt())
                    .set(APPROVAL_REQUEST.VERSION, 0)
                    .returning(APPROVAL_REQUEST.ID)
                    .fetchSingle(APPROVAL_REQUEST.ID);
        } catch (DuplicateKeyException e) {
            // UC-024 A6 / BR-009: another request for the same subject was submitted first.
            throw new ApprovalConcurrentChangeException("同じ対象の稟議が、別の操作で申請されました。");
        }
        insertValues(id, request.subject().attributes(), request.subject().conditions());
        insertSteps(id, request.round(), request.steps());
        return id;
    }

    @Override
    @Transactional
    public void resubmit(long requestId, int expectedVersion, ResubmittedRound round) {
        int updated = ctx.update(APPROVAL_REQUEST)
                .set(APPROVAL_REQUEST.TITLE, round.title())
                .set(APPROVAL_REQUEST.BODY, round.body())
                .set(APPROVAL_REQUEST.ROUTE_RULE_ID, round.routeRuleId())
                .set(APPROVAL_REQUEST.STATUS, round.status().name())
                .set(APPROVAL_REQUEST.ROUND, round.round())
                .set(APPROVAL_REQUEST.SUBMITTED_AT, round.submittedAt())
                .set(APPROVAL_REQUEST.VERSION, APPROVAL_REQUEST.VERSION.plus(1))
                .where(APPROVAL_REQUEST.ID.eq(requestId))
                .and(APPROVAL_REQUEST.VERSION.eq(expectedVersion))
                // UC-028 BR-001: only a sent-back request is resubmitted
                .and(APPROVAL_REQUEST.STATUS.eq(ApprovalStatus.DRAFT.name()))
                .execute();
        if (updated == 0) {
            throw new ApprovalConcurrentChangeException("稟議が、別の操作で変わりました。");
        }
        // UC-028 BR-005: attributes and conditions are the values the business set now
        ctx.deleteFrom(APPROVAL_ATTRIBUTE).where(APPROVAL_ATTRIBUTE.REQUEST_ID.eq(requestId)).execute();
        ctx.deleteFrom(APPROVAL_CONDITION).where(APPROVAL_CONDITION.REQUEST_ID.eq(requestId)).execute();
        insertValues(requestId, round.attributes(), round.conditions());
        // UC-028 BR-006: the steps of earlier rounds stay as they are; the new round gets its own
        insertSteps(requestId, round.round(), round.steps());
    }

    private void insertValues(long requestId, Map<String, BigDecimal> attributes, Map<String, String> conditions) {
        attributes.forEach((name, value) -> ctx.insertInto(APPROVAL_ATTRIBUTE)
                .set(APPROVAL_ATTRIBUTE.REQUEST_ID, requestId)
                .set(APPROVAL_ATTRIBUTE.NAME, name)
                .set(APPROVAL_ATTRIBUTE.VALUE, value)
                .execute());
        conditions.forEach((name, value) -> ctx.insertInto(APPROVAL_CONDITION)
                .set(APPROVAL_CONDITION.REQUEST_ID, requestId)
                .set(APPROVAL_CONDITION.NAME, name)
                .set(APPROVAL_CONDITION.VALUE, value)
                .execute());
    }

    private void insertSteps(long requestId, int round, List<RouteStep> steps) {
        for (RouteStep step : steps) {
            ctx.insertInto(APPROVAL_STEP)
                    .set(APPROVAL_STEP.REQUEST_ID, requestId)
                    .set(APPROVAL_STEP.ROUND, round)
                    .set(APPROVAL_STEP.KIND, step.kind().name())
                    .set(APPROVAL_STEP.STEP_ORDER, step.order())
                    .set(APPROVAL_STEP.POSITION_ID, step.positionId())
                    .set(APPROVAL_STEP.ACTOR_ID, step.actorId())
                    .set(APPROVAL_STEP.AUTO_ASSIGNED, step.autoAssigned())
                    .set(APPROVAL_STEP.DECISION, "PENDING")
                    .execute();
        }
    }

    @Override
    public Optional<ApprovalRequestState> find(long requestId, boolean lock) {
        var query = ctx.selectFrom(APPROVAL_REQUEST).where(APPROVAL_REQUEST.ID.eq(requestId));
        var found = lock ? query.forUpdate().fetchOptional() : query.fetchOptional();
        return found.map(r -> new ApprovalRequestState(
                r.getId(), r.getOrganizationId(), r.getSubjectType(), r.getSubjectRef(), r.getTitle(), r.getBody(),
                r.getDrafterId(), ApprovalStatus.valueOf(r.getStatus()), r.getRound(), r.getVersion(),
                r.getSubmittedAt(),
                ctx.selectFrom(APPROVAL_ATTRIBUTE).where(APPROVAL_ATTRIBUTE.REQUEST_ID.eq(requestId))
                        .fetchMap(APPROVAL_ATTRIBUTE.NAME, APPROVAL_ATTRIBUTE.VALUE),
                ctx.selectFrom(APPROVAL_CONDITION).where(APPROVAL_CONDITION.REQUEST_ID.eq(requestId))
                        .fetchMap(APPROVAL_CONDITION.NAME, APPROVAL_CONDITION.VALUE),
                ctx.selectFrom(APPROVAL_STEP)
                        // the steps of the current round only
                        .where(APPROVAL_STEP.REQUEST_ID.eq(requestId)).and(APPROVAL_STEP.ROUND.eq(r.getRound()))
                        .orderBy(APPROVAL_STEP.ID)
                        .fetch(st -> new StepState(st.getId(), StepKind.valueOf(st.getKind()), st.getStepOrder(),
                                st.getPositionId(), st.getActorId(), StepDecision.valueOf(st.getDecision()),
                                st.getComment(), st.getDecidedAt()))));
    }

    @Override
    @Transactional
    public void apply(long requestId, int expectedVersion, DecisionResult result) {
        int updated = ctx.update(APPROVAL_REQUEST)
                .set(APPROVAL_REQUEST.STATUS, result.status().name())
                .set(APPROVAL_REQUEST.DECIDED_AT, result.decidedAt())
                .set(APPROVAL_REQUEST.VERSION, APPROVAL_REQUEST.VERSION.plus(1))
                .where(APPROVAL_REQUEST.ID.eq(requestId))
                .and(APPROVAL_REQUEST.VERSION.eq(expectedVersion))
                .execute();
        if (updated == 0) {
            throw new ApprovalConcurrentChangeException("稟議が、別の操作で変わりました。");
        }
        for (DecisionResult.StepChange change : result.stepChanges()) {
            ctx.update(APPROVAL_STEP)
                    .set(APPROVAL_STEP.DECISION, change.decision().name())
                    .set(APPROVAL_STEP.COMMENT, change.comment())
                    .set(APPROVAL_STEP.DECIDED_AT, change.decidedAt())
                    .where(APPROVAL_STEP.ID.eq(change.stepId()))
                    .and(APPROVAL_STEP.REQUEST_ID.eq(requestId))
                    .execute();
        }
    }

    @Override
    public List<AssignedApproval> findAssigned(long userId) {
        // requests where the user has an undecided deciding step in the current round
        List<Long> ids = ctx.selectDistinct(APPROVAL_REQUEST.ID)
                .from(APPROVAL_REQUEST)
                .join(APPROVAL_STEP).on(APPROVAL_STEP.REQUEST_ID.eq(APPROVAL_REQUEST.ID)
                        .and(APPROVAL_STEP.ROUND.eq(APPROVAL_REQUEST.ROUND)))
                .where(APPROVAL_REQUEST.STATUS.in(ApprovalStatus.IN_CONSULTATION.name(), ApprovalStatus.IN_APPROVAL.name()))
                .and(APPROVAL_STEP.ACTOR_ID.eq(userId))
                .and(APPROVAL_STEP.KIND.ne(StepKind.REPORT.name()))
                .and(APPROVAL_STEP.DECISION.eq(StepDecision.PENDING.name()))
                .orderBy(APPROVAL_REQUEST.ID)
                .fetch(APPROVAL_REQUEST.ID);
        return ids.stream()
                .flatMap(id -> find(id, false).stream())
                // only where it is the user's turn now (UC-026 BR-001)
                .filter(state -> state.blockedReason(userId).isEmpty())
                .map(state -> new AssignedApproval(state.id(), state.title(), state.status(),
                        state.status() == ApprovalStatus.IN_CONSULTATION ? StepKind.CONSULTATION : StepKind.MAIN,
                        state.submittedAt()))
                .toList();
    }
}
