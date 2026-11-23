package com.example.syndicateloan.facility.domain;

import java.time.LocalDate;
import java.util.Objects;

/**
 * 引き出せる期間。
 *
 * @param startDate 初日
 * @param endDate   末日
 */
public record DrawdownPeriod(LocalDate startDate, LocalDate endDate) {

    public DrawdownPeriod {
        Objects.requireNonNull(startDate, "startDate");
        Objects.requireNonNull(endDate, "endDate");
        // UC-015 BR-003: The last day of the drawdown period comes after the first day.
        if (!endDate.isAfter(startDate)) {
            throw new IllegalArgumentException("endDate must be after startDate");
        }
    }
}
