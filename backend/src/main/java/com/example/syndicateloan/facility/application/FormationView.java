package com.example.syndicateloan.facility.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * UC-015 step 2(と A1、A2、A3): 組成を始めたときに示す内容。
 *
 * @param dealId                   融資案件
 * @param currency                 通貨
 * @param blockedReason            組成できない理由。組成できるなら null
 * @param investors                参加を応諾した出資者と、その応諾額と今の持分
 * @param totalAcceptedAmount      応諾額の合計
 * @param leadCommitmentAmount     今のリード銀行自身のコミットメント額
 * @param facility                 今のファシリティ。まだなければ null
 * @param participationFingerprint 確定のときに送り返す、参加の回答の指紋(A6)
 */
public record FormationView(
        long dealId,
        String currency,
        BlockedReason blockedReason,
        List<Investor> investors,
        BigDecimal totalAcceptedAmount,
        BigDecimal leadCommitmentAmount,
        FacilitySummary facility,
        String participationFingerprint) {

    public FormationView {
        investors = List.copyOf(investors);
    }

    /** 組成できない理由。 */
    public enum BlockedReason {
        /** A1: 参加を応諾した出資者がいない。 */
        NO_ACCEPTED_INVESTOR,
        /** A2: ファシリティが確定しているか、稟議にかかっている。 */
        FACILITY_LOCKED,
        /** 融資案件が組成準備中でない(前提条件を満たさない)。 */
        DEAL_NOT_PREPARING,
        /** 融資案件が取下げか謝絶になっている。 */
        DEAL_CLOSED
    }

    /**
     * 参加を応諾した出資者。
     *
     * @param organizationId   出資者
     * @param commitmentAmount 応諾したコミットメント額(組成の目安)
     * @param shareAmount      今のファシリティの持分。ファシリティがまだなければ応諾額
     */
    public record Investor(long organizationId, BigDecimal commitmentAmount, BigDecimal shareAmount) {
    }

    /**
     * 今のファシリティ。
     *
     * @param id               ファシリティ
     * @param status           状態
     * @param underApproval    組成の稟議にかかっている
     * @param commitmentAmount コミットメント額
     * @param startDate        引き出せる期間の初日
     * @param endDate          引き出せる期間の末日
     * @param version          確定のときに送り返すバージョン(NFR-003)
     */
    public record FacilitySummary(
            long id,
            String status,
            boolean underApproval,
            BigDecimal commitmentAmount,
            LocalDate startDate,
            LocalDate endDate,
            int version) {
    }
}
