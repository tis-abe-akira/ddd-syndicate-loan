package com.example.syndicateloan.syndicate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ファシリティの組成(UC-015)に要る、融資案件の情報。
 *
 * @param id                   融資案件
 * @param version              融資案件のバージョン
 * @param leadOrganizationId   リード銀行
 * @param currency             融資案件の通貨
 * @param status               融資案件の状態
 * @param acceptedInvestors    参加を応諾した出資者
 */
public record DealForFormation(
        long id,
        int version,
        long leadOrganizationId,
        String currency,
        DealStatus status,
        List<AcceptedInvestor> acceptedInvestors) {

    public DealForFormation {
        acceptedInvestors = List.copyOf(acceptedInvestors);
    }

    /**
     * 参加を応諾した出資者と、その応諾額の指紋。組成を見たときと確定したときで違えば、
     * その間に参加の回答が変わった(UC-015 A6)。
     */
    public String participationFingerprint() {
        String text = acceptedInvestors.stream()
                .sorted(java.util.Comparator.comparingLong(AcceptedInvestor::organizationId))
                .map(investor -> investor.organizationId() + "="
                        + investor.commitmentAmount().stripTrailingZeros().toPlainString())
                .collect(Collectors.joining(";"));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
