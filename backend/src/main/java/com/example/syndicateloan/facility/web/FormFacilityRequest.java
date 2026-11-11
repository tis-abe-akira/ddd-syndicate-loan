package com.example.syndicateloan.facility.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * UC-015 step 3、4: 組成の確定。業務ルール(A5)の検証はサービスがまとめて行い、ここでは形だけを確かめる。
 *
 * @param participationFingerprint 組成を始めたときに受け取った、参加の回答の指紋
 * @param investorShares           参加を応諾した出資者ごとの持分
 * @param leadCommitmentAmount     リード銀行自身のコミットメント額(持たないなら0)
 * @param startDate                引き出せる期間の初日
 * @param endDate                  引き出せる期間の末日
 */
public record FormFacilityRequest(
        @NotBlank String participationFingerprint,
        @NotNull @Valid List<InvestorShareRequest> investorShares,
        @Digits(integer = 15, fraction = 2) BigDecimal leadCommitmentAmount,
        LocalDate startDate,
        LocalDate endDate) {

    /**
     * 一つの出資者の持分。
     *
     * @param organizationId 出資者
     * @param amount         持分
     */
    public record InvestorShareRequest(
            @NotNull Long organizationId,
            @Digits(integer = 15, fraction = 2) BigDecimal amount) {
    }
}
