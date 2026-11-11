package com.example.syndicateloan.facility.domain;

import com.example.syndicateloan.shared.SharePie;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * 融資案件から組成した、借り手が引き出せる融資枠と、そのシェアパイ(ADR-001)。
 *
 * @param id                組成中として登録する前は null
 * @param dealId            組成元の融資案件
 * @param currency          通貨。融資案件の通貨
 * @param period            引き出せる期間
 * @param sharePie          参加金融機関ごとの持分
 * @param status            状態
 * @param approvalRequestId 組成の稟議。起案する前は null
 * @param version           楽観的排他制御のバージョン
 */
public record Facility(
        Long id,
        long dealId,
        String currency,
        DrawdownPeriod period,
        SharePie sharePie,
        FacilityStatus status,
        Long approvalRequestId,
        int version) {

    public Facility {
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(period, "period");
        Objects.requireNonNull(sharePie, "sharePie");
        Objects.requireNonNull(status, "status");
        // UC-015 BR-001: Only institutions with a share above zero are in the pie.
        if (sharePie.isEmpty() || sharePie.shares().values().stream().anyMatch(a -> a.signum() <= 0)) {
            throw new IllegalArgumentException("A facility needs shares above zero only");
        }
    }

    /** UC-015 step 6: 組成中のファシリティを作る(まだ登録していない)。 */
    public static Facility draft(long dealId, String currency, DrawdownPeriod period, SharePie sharePie) {
        return new Facility(null, dealId, currency, period, sharePie, FacilityStatus.DRAFT, null, 0);
    }

    // UC-015 BR-002: The commitment equals the total of the share pie to the yen.
    public BigDecimal commitmentAmount() {
        return sharePie.total();
    }

    /**
     * 組成を直せるか(UC-015 A2、A3)。FIXED と、稟議にかかっているものは直せない。
     *
     * <p>否決された稟議のファシリティは直せる(A3)が、稟議の状態はワークフロー(UC-024、Issue #31)が
     * できるまで分からないので、稟議にかかったものはすべて直せないとする。
     */
    public boolean isRevisable() {
        // UC-015 BR-006: A FIXED facility does not change.
        // UC-015 BR-007: Shares and period do not change while the approval is in progress.
        return status == FacilityStatus.DRAFT && approvalRequestId == null;
    }

    /** UC-015 A3: 持分と引き出せる期間を直したファシリティ。 */
    public Facility revise(DrawdownPeriod newPeriod, SharePie newSharePie) {
        if (!isRevisable()) {
            throw new IllegalStateException("The facility cannot be revised: " + id);
        }
        return new Facility(id, dealId, currency, newPeriod, newSharePie, status, approvalRequestId, version);
    }
}
