package com.example.syndicateloan.organization;

/** 操作している利用者が分からないか、無効である。API では 401 Unauthorized になる。 */
public class UnauthenticatedException extends RuntimeException {

    public UnauthenticatedException(String message) {
        super(message);
    }
}
