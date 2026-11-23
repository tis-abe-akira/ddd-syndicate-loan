package com.example.archfixture.compliant.loan.persistence;

import com.example.archfixture.compliant.loan.domain.Loan;
import com.example.archfixture.compliant.loan.domain.LoanRepository;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public class JooqLoanRepository implements LoanRepository {
    @Override
    public Loan find(long id) {
        return new Loan(0);
    }
}
