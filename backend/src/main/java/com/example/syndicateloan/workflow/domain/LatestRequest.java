package com.example.syndicateloan.workflow.domain;

import com.example.syndicateloan.workflow.ApprovalStatus;

/**
 * 同じ稟議の種類と稟議の対象の、最後の稟議。
 *
 * @param id     稟議
 * @param status 状態
 */
public record LatestRequest(long id, ApprovalStatus status) {
}
