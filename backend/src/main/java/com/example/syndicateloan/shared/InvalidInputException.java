package com.example.syndicateloan.shared;

import java.util.List;

/**
 * 入力の項目が業務ルールを満たさない(直すべき項目を示す)。API では 400 Bad Request になる。
 *
 * @param errors 直すべき項目と、その理由
 */
public class InvalidInputException extends RuntimeException {

    private final List<FieldError> errors;

    public InvalidInputException(List<FieldError> errors) {
        super("入力に直すべき項目があります。");
        this.errors = List.copyOf(errors);
    }

    public List<FieldError> errors() {
        return errors;
    }

    /** 直すべき項目と、その理由。 */
    public record FieldError(String field, String message) {
    }
}
