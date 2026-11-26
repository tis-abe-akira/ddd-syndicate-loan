package com.example.syndicateloan.workflow.persistence;

import static com.example.syndicateloan.workflow.persistence.jooq.generated.Tables.APPROVAL_ATTRIBUTE;
import static com.example.syndicateloan.workflow.persistence.jooq.generated.Tables.APPROVAL_CONDITION;
import static com.example.syndicateloan.workflow.persistence.jooq.generated.Tables.APPROVAL_REQUEST;
import static com.example.syndicateloan.workflow.persistence.jooq.generated.Tables.APPROVAL_STEP;

import com.example.syndicateloan.workflow.ApprovalConcurrentChangeException;
import com.example.syndicateloan.workflow.ApprovalStatus;
import com.example.syndicateloan.workflow.domain.ApprovalRequestRepository;
import com.example.syndicateloan.workflow.domain.LatestRequest;
import com.example.syndicateloan.workflow.domain.NewApprovalRequest;
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
        request.subject().attributes().forEach((name, value) -> ctx.insertInto(APPROVAL_ATTRIBUTE)
                .set(APPROVAL_ATTRIBUTE.REQUEST_ID, id)
                .set(APPROVAL_ATTRIBUTE.NAME, name)
                .set(APPROVAL_ATTRIBUTE.VALUE, value)
                .execute());
        request.subject().conditions().forEach((name, value) -> ctx.insertInto(APPROVAL_CONDITION)
                .set(APPROVAL_CONDITION.REQUEST_ID, id)
                .set(APPROVAL_CONDITION.NAME, name)
                .set(APPROVAL_CONDITION.VALUE, value)
                .execute());
        for (RouteStep step : request.steps()) {
            ctx.insertInto(APPROVAL_STEP)
                    .set(APPROVAL_STEP.REQUEST_ID, id)
                    .set(APPROVAL_STEP.ROUND, request.round())
                    .set(APPROVAL_STEP.KIND, step.kind().name())
                    .set(APPROVAL_STEP.STEP_ORDER, step.order())
                    .set(APPROVAL_STEP.POSITION_ID, step.positionId())
                    .set(APPROVAL_STEP.ACTOR_ID, step.actorId())
                    .set(APPROVAL_STEP.AUTO_ASSIGNED, step.autoAssigned())
                    .set(APPROVAL_STEP.DECISION, "PENDING")
                    .execute();
        }
        return id;
    }
}
