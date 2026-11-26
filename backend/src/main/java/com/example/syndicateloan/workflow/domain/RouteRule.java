package com.example.syndicateloan.workflow.domain;

import com.example.syndicateloan.workflow.StepKind;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 利用組織が設定する承認経路の規則。
 *
 * @param id             規則
 * @param organizationId 規則を設定した利用組織
 * @param subjectType    稟議の種類
 * @param priority       複数の規則が合うときの優先順位。小さいほうが優先
 * @param version        楽観的排他制御のバージョン
 * @param conditions     経路属性の範囲
 * @param steps          段の案
 */
public record RouteRule(
        long id,
        long organizationId,
        String subjectType,
        int priority,
        int version,
        List<RangeCondition> conditions,
        List<RuleStep> steps) {

    public RouteRule {
        conditions = List.copyOf(conditions);
        steps = List.copyOf(steps);
    }

    // UC-024 BR-001: a rule matches when the attributes meet every one of its conditions.
    public boolean matches(Map<String, BigDecimal> attributes) {
        return conditions.stream().allMatch(condition -> condition.isMetBy(attributes));
    }

    /** UC-024 BR-001: 合う規則のうち、優先順位の値が最も小さいもの。 */
    public static Optional<RouteRule> choose(List<RouteRule> rules, Map<String, BigDecimal> attributes) {
        return rules.stream()
                .filter(rule -> rule.matches(attributes))
                .min(Comparator.comparingInt(RouteRule::priority));
    }

    /** 段の種類ごとの段。メイントラックは順番に並べる。 */
    public List<RuleStep> steps(StepKind kind) {
        return steps.stream()
                .filter(step -> step.kind() == kind)
                .sorted(Comparator.comparing(step -> step.order() == null ? 0 : step.order()))
                .toList();
    }
}
