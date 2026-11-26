package com.example.syndicateloan.workflow.persistence;

import static com.example.syndicateloan.workflow.persistence.jooq.generated.Tables.ROUTE_RULE;
import static com.example.syndicateloan.workflow.persistence.jooq.generated.Tables.ROUTE_RULE_CONDITION;
import static com.example.syndicateloan.workflow.persistence.jooq.generated.Tables.ROUTE_RULE_STEP;

import com.example.syndicateloan.workflow.StepKind;
import com.example.syndicateloan.workflow.domain.RangeCondition;
import com.example.syndicateloan.workflow.domain.RouteRule;
import com.example.syndicateloan.workflow.domain.RouteRuleRepository;
import com.example.syndicateloan.workflow.domain.RuleStep;
import java.util.List;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** {@link RouteRuleRepository} の jOOQ のアダプタ。 */
@Repository
@Transactional(readOnly = true)
public class JooqRouteRuleRepository implements RouteRuleRepository {

    private final DSLContext ctx;

    public JooqRouteRuleRepository(DSLContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public List<RouteRule> findBySubjectType(long organizationId, String subjectType) {
        return read(organizationId, subjectType, false);
    }

    @Override
    @Transactional
    public List<RouteRule> lockBySubjectType(long organizationId, String subjectType) {
        return read(organizationId, subjectType, true);
    }

    private List<RouteRule> read(long organizationId, String subjectType, boolean lock) {
        var query = ctx.selectFrom(ROUTE_RULE)
                // UC-024 BR-001: only the drafter's organization's rules for the same subject type.
                .where(ROUTE_RULE.ORGANIZATION_ID.eq(organizationId))
                .and(ROUTE_RULE.SUBJECT_TYPE.eq(subjectType))
                .orderBy(ROUTE_RULE.PRIORITY);
        // UC-024 A6 / NFR-003: on submit, the rules cannot change until the request is registered.
        var rules = lock ? query.forShare().fetch() : query.fetch();
        return rules.map(rule -> new RouteRule(
                        rule.getId(),
                        rule.getOrganizationId(),
                        rule.getSubjectType(),
                        rule.getPriority(),
                        rule.getVersion(),
                        conditions(rule.getId()),
                        steps(rule.getId())));
    }

    private List<RangeCondition> conditions(long ruleId) {
        return ctx.selectFrom(ROUTE_RULE_CONDITION)
                .where(ROUTE_RULE_CONDITION.ROUTE_RULE_ID.eq(ruleId))
                .orderBy(ROUTE_RULE_CONDITION.ID)
                .fetch(c -> new RangeCondition(c.getAttributeName(), c.getMinValue(), c.getMaxValue()));
    }

    private List<RuleStep> steps(long ruleId) {
        return ctx.selectFrom(ROUTE_RULE_STEP)
                .where(ROUTE_RULE_STEP.ROUTE_RULE_ID.eq(ruleId))
                .orderBy(ROUTE_RULE_STEP.ID)
                .fetch(s -> new RuleStep(StepKind.valueOf(s.getKind()), s.getStepOrder(), s.getPositionId(),
                        s.getDefaultUserId()));
    }
}
