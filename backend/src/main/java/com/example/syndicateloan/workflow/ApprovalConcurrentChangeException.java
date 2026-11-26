package com.example.syndicateloan.workflow;

/** UC-024 A6: 起案の間に、別の操作で規則か同じ対象の稟議が変わった。何も登録していない。 */
public class ApprovalConcurrentChangeException extends RuntimeException {

    public ApprovalConcurrentChangeException(String message) {
        super(message);
    }
}
