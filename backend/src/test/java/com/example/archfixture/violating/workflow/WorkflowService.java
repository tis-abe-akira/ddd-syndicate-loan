package com.example.archfixture.violating.workflow;

import com.example.archfixture.violating.loan.domain.Loan;

/** 違反: workflow が業務のモジュール(loan)に依存する。 */
public class WorkflowService {
    Loan loan;
}
