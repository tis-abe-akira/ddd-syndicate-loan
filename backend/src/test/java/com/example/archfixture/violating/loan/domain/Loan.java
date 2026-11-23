package com.example.archfixture.violating.loan.domain;

/** 違反: ドメイン型が final でも record でもない(可変になりうる)。 */
public class Loan {
    long principal;
}
