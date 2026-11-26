package com.example.syndicateloan.workflow;

/** UC-024 の前提条件: 起案者が業務ユーザーのロールを持たない。何も登録していない。 */
public class ApprovalForbiddenException extends RuntimeException {

    public ApprovalForbiddenException(String message) {
        super(message);
    }
}
