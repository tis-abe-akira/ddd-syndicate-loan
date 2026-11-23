package com.example.syndicateloan.shared;

/** 利用者のロールでは許されない操作。API では 403 Forbidden になる。 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
