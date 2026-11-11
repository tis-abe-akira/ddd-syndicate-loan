package com.example.syndicateloan.syndicate;

import java.math.BigDecimal;

/**
 * 融資案件への参加を応諾した出資者。
 *
 * @param organizationId    出資者の利用組織
 * @param commitmentAmount  応諾したコミットメント額(組成の目安、UC-015 BR-009)
 */
public record AcceptedInvestor(long organizationId, BigDecimal commitmentAmount) {
}
