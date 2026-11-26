package com.example.syndicateloan.workflow.domain;

import java.util.Optional;

/** 稟議のポート。 */
public interface ApprovalRequestRepository {

    /** 同じ利用組織・稟議の種類・稟議の対象の、最後に登録した稟議。 */
    Optional<LatestRequest> findLatest(long organizationId, String subjectType, String subjectRef);

    /**
     * 稟議と、その経路属性・条件・承認経路を登録し、id を返す。
     *
     * @throws com.example.syndicateloan.workflow.ApprovalConcurrentChangeException
     *         同じ対象の稟議が、別の操作で先に申請されたとき(UC-024 A6)
     */
    long insert(NewApprovalRequest request);
}
