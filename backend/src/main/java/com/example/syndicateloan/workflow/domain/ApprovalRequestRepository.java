package com.example.syndicateloan.workflow.domain;

import com.example.syndicateloan.workflow.AssignedApproval;
import java.util.List;
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

    /**
     * 差し戻された稟議を、新しい回として申請し直す(UC-028 step 8)。前の回の段と判断は残す(UC-028 BR-006)。
     *
     * @throws com.example.syndicateloan.workflow.ApprovalConcurrentChangeException 読んだ後に変わっていたとき
     */
    void resubmit(long requestId, int expectedVersion, ResubmittedRound round);

    /** 稟議と、今の回の段。lock なら、呼び出し側のトランザクションが終わるまで行をロックする。 */
    Optional<ApprovalRequestState> find(long requestId, boolean lock);

    /**
     * 判断の結果を記録し、稟議のバージョンを上げる。
     *
     * @throws com.example.syndicateloan.workflow.ApprovalConcurrentChangeException 読んだ後に変わっていたとき
     */
    void apply(long requestId, int expectedVersion, DecisionResult result);

    /** 利用者が今判断する番の稟議。 */
    List<AssignedApproval> findAssigned(long userId);
}
