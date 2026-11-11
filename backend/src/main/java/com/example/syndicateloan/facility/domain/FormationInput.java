package com.example.syndicateloan.facility.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * UC-015 step 3: 担当者が決めた組成の内容。検証はまだしていない。
 *
 * @param investorShares       出資者の利用組織 id と、その持分
 * @param leadCommitmentAmount リード銀行自身のコミットメント額(持たないなら0円)
 * @param startDate            引き出せる期間の初日
 * @param endDate              引き出せる期間の末日
 */
public record FormationInput(
        Map<Long, BigDecimal> investorShares,
        BigDecimal leadCommitmentAmount,
        LocalDate startDate,
        LocalDate endDate) {

    public FormationInput {
        investorShares = Map.copyOf(investorShares);
    }
}
