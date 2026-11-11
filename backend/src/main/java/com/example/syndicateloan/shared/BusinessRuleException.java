package com.example.syndicateloan.shared;

/**
 * 業務ルールに反するため、受け付けられない要求(リクエストの形は正しい)。
 * API では 409 Conflict の Problem Details になる。
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
