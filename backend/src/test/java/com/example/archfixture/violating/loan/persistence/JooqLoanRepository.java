package com.example.archfixture.violating.loan.persistence;

import com.example.archfixture.violating.loan.domain.LoanRepository;
import org.springframework.transaction.annotation.Transactional;

/** 違反: @Transactional なクラスが、default メソッドを持つポートを実装する。 */
@Transactional
public class JooqLoanRepository implements LoanRepository {
    @Override
    public long count() {
        return 0;
    }
}
