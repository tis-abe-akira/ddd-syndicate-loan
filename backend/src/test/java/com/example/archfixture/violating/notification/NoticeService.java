package com.example.archfixture.violating.notification;

import com.example.archfixture.violating.loan.domain.Loan;

/** 違反: notification が業務のモジュール(loan)に依存する。 */
public class NoticeService {
    Loan loan;
}
