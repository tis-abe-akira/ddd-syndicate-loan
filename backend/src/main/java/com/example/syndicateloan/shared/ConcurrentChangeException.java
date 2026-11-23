package com.example.syndicateloan.shared;

/**
 * 確かめた後、記録するまでの間に、別の操作が前提を変えた。何も記録していない。
 * API では 409 Conflict になり、利用者はやり直す。
 */
public class ConcurrentChangeException extends RuntimeException {

    public ConcurrentChangeException(String message) {
        super(message);
    }
}
