package com.example.archfixture.violating.loan.domain;

import com.example.archfixture.violating.loan.web.RepaymentController;
import org.springframework.stereotype.Component;

/** 違反: ドメインが外側の層(web)と Spring に依存する。 */
@Component
public record Repayment(long amount, RepaymentController controller) {
}
