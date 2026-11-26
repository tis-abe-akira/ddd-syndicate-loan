package com.example.syndicateloan.workflow;

import java.time.LocalDateTime;

/**
 * UC-025・UC-026 step 1: 利用者が今判断する番の稟議。
 *
 * @param requestId   稟議
 * @param title       件名
 * @param status      状態
 * @param kind        利用者の段の種類(合議先かメイントラック)
 * @param submittedAt 申請の日時
 */
public record AssignedApproval(long requestId, String title, ApprovalStatus status, StepKind kind,
        LocalDateTime submittedAt) {
}
