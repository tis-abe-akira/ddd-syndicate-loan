package com.example.syndicateloan.shared;

/**
 * 利用者が指した対象が存在しないか、利用者の組織からは見えない(NFR-007)。
 * API では 404 Not Found の Problem Details になる。
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
