package com.example.syndicateloan.workflow;

import java.util.List;

/** UC-024 A5: 承認経路か件名が正しくない。直すべき項目を示す。何も登録していない。 */
public class InvalidApprovalDraftException extends RuntimeException {

    private final List<DraftError> errors;

    public InvalidApprovalDraftException(List<DraftError> errors) {
        super("稟議に直すべき項目があります。");
        this.errors = List.copyOf(errors);
    }

    public List<DraftError> errors() {
        return errors;
    }

    /**
     * 直すべき項目と、その理由。
     *
     * @param field   title、steps、steps[添字] のいずれか
     * @param message 理由
     */
    public record DraftError(String field, String message) {
    }
}
