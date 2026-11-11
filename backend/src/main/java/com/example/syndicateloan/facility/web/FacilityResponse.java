package com.example.syndicateloan.facility.web;

import com.example.syndicateloan.facility.domain.Facility;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * UC-015 step 6: 組成中として登録したファシリティと、そのシェアパイ。
 *
 * @param id               ファシリティ
 * @param dealId           融資案件
 * @param status           状態
 * @param commitmentAmount コミットメント額(シェアパイの合計)
 * @param currency         通貨
 * @param startDate        引き出せる期間の初日
 * @param endDate          引き出せる期間の末日
 * @param shares           参加金融機関ごとの持分
 */
public record FacilityResponse(
        long id,
        long dealId,
        String status,
        BigDecimal commitmentAmount,
        String currency,
        LocalDate startDate,
        LocalDate endDate,
        List<ShareResponse> shares) {

    static FacilityResponse of(Facility facility) {
        return new FacilityResponse(
                facility.id(),
                facility.dealId(),
                facility.status().name(),
                facility.commitmentAmount(),
                facility.currency(),
                facility.period().startDate(),
                facility.period().endDate(),
                facility.sharePie().shares().entrySet().stream()
                        .map(e -> new ShareResponse(e.getKey(), e.getValue()))
                        .toList());
    }

    /**
     * 一つの参加金融機関の持分。
     *
     * @param organizationId 参加金融機関
     * @param amount         持分
     */
    public record ShareResponse(long organizationId, BigDecimal amount) {
    }
}
