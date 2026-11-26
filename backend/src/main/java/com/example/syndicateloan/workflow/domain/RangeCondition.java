package com.example.syndicateloan.workflow.domain;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 承認経路の規則が合うための、一つの経路属性の範囲。
 *
 * @param attributeName 経路属性の名前
 * @param min           下限(この値を含む)。なければ null
 * @param max           上限(この値を含まない)。なければ null
 */
public record RangeCondition(String attributeName, BigDecimal min, BigDecimal max) {

    // UC-024 BR-001: lower bound inclusive, upper exclusive; a missing attribute never matches.
    public boolean isMetBy(Map<String, BigDecimal> attributes) {
        BigDecimal value = attributes.get(attributeName);
        if (value == null) {
            return false;
        }
        return (min == null || value.compareTo(min) >= 0) && (max == null || value.compareTo(max) < 0);
    }
}
