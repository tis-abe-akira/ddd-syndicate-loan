package com.example.syndicateloan.workflow.domain;

/** 承認経路の段の判断。 */
public enum StepDecision {
    PENDING, APPROVED, SENT_BACK, REJECTED, NOTIFIED, SKIPPED
}
