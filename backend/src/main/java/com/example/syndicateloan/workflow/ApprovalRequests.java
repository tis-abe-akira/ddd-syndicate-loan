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

    /**
     * UC-028 step 1〜4: 差し戻された稟議の再申請を始め、前の回の判断と、新しい回の承認経路の案を示す。何も登録しない。
     *
     * @throws ApprovalForbiddenException  起案者が業務ユーザーでない(前提条件)
     * @throws ApprovalNotAllowedException UC-028 A1、A2、A3
     */
    ResubmissionProposal proposeResubmission(ApprovalSubject subject, ActingUser drafter);

    /**
     * UC-028 step 6〜9: 差し戻された稟議を、新しい回として再申請する。
     *
     * @throws ApprovalForbiddenException         起案者が業務ユーザーでない(前提条件)
     * @throws ApprovalNotAllowedException        UC-028 A1、A2、A3
     * @throws InvalidApprovalDraftException      UC-028 A5
     * @throws ApprovalConcurrentChangeException  UC-028 A6
     */
    SubmittedApproval resubmit(Resubmission resubmission, ActingUser drafter);
}
