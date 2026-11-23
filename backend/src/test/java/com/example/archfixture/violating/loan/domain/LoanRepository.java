package com.example.archfixture.violating.loan.domain;

/** 違反の素: ポートに default メソッドがある。 */
public interface LoanRepository {
    long count();

    default boolean isEmpty() {
        return count() == 0;
    }
}
