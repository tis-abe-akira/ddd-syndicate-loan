package com.example.syndicateloan.workflow;

import com.example.syndicateloan.organization.ActingUser;

/**
 * 稟議の公開 API(ADR-002)。業務のユースケースが、稟議の中身を決めて呼ぶ。
 * 画面から呼ぶ REST は業務の側が持つ(#41)。
 */
public interface ApprovalRequests {

    /**
     * UC-024 step 1〜4: 起案を始め、規則の案から承認経路と件名の案を作る。何も登録しない。
     *
     * @throws ApprovalForbiddenException  起案者が業務ユーザーでない(前提条件)
     * @throws ApprovalNotAllowedException A1、A2、A3
     */
    ProposedRoute propose(ApprovalSubject subject, ActingUser drafter);

    /**
     * UC-024 step 6〜9: 稟議を申請する。
     *
     * @throws ApprovalForbiddenException         起案者が業務ユーザーでない(前提条件)
     * @throws ApprovalNotAllowedException        A1、A2、A3
     * @throws InvalidApprovalDraftException      A5
     * @throws ApprovalConcurrentChangeException  A6
     */
    SubmittedApproval submit(ApprovalDraft draft, ActingUser drafter);
}
