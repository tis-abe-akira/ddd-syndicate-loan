package com.example.syndicateloan.shared;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 参加金融機関ごとの持分を金額で持つ値(ADR-001)。比率は金額から導く。
 * ファシリティとローンがそれぞれ持ち、互いに独立して変わる。
 *
 * @param shares 参加金融機関の利用組織 id と、その持分の金額
 */
public record SharePie(Map<Long, BigDecimal> shares) {

    public SharePie {
        Objects.requireNonNull(shares, "shares");
        Map<Long, BigDecimal> copy = new LinkedHashMap<>();
        shares.forEach((organizationId, amount) -> {
            Objects.requireNonNull(organizationId, "organizationId");
            Objects.requireNonNull(amount, "amount");
            if (amount.signum() < 0) {
                throw new IllegalArgumentException("A share must not be negative: " + organizationId);
            }
            copy.put(organizationId, amount);
        });
        shares = Collections.unmodifiableMap(copy);
    }

    /** 持分の合計。 */
    public BigDecimal total() {
        return shares.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** 持分を持つ参加金融機関がいないとき true。 */
    public boolean isEmpty() {
        return shares.isEmpty();
    }
}
