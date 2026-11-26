package com.example.syndicateloan.workflow;

import com.example.syndicateloan.organization.ActingUser;
import java.util.List;

/** 合議と承認の公開 API(UC-025、UC-026)。判断は業務の値を含まないので、画面からも使う。 */
public interface ApprovalDecisions {

    /** UC-025・UC-026 step 1: 利用者が今判断する番の稟議。 */
    List<AssignedApproval> assigned(ActingUser user);

    /**
     * UC-025・UC-026 step 2: 稟議を開く。
     *
     * @throws ApprovalNotFoundException A1
     */
    ApprovalView view(long requestId, ActingUser user);

    /**
     * UC-025・UC-026 step 3〜8: 判断を記録する。
     *
     * @throws ApprovalNotFoundException        A1
     * @throws DecisionNotAllowedException      A2
     * @throws InvalidApprovalDraftException    A5(コメントがない)
     * @throws ApprovalConcurrentChangeException A6
     * @throws ApprovalForbiddenException       業務ユーザーでない(前提条件)
     */
    ApprovalView decide(long requestId, DecisionCommand command, ActingUser user);
}
