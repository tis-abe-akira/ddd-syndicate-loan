package com.example.archfixture.compliant.loan.domain;

/** 適合: ポートは抽象メソッドだけ。 */
public interface LoanRepository {
    Loan find(long id);
}
