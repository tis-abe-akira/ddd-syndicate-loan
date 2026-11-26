package com.example.syndicateloan.workflow;

/** 稟議の状態。DRAFT は差し戻された稟議。 */
public enum ApprovalStatus {
    DRAFT, IN_CONSULTATION, IN_APPROVAL, APPROVED, REJECTED
}
