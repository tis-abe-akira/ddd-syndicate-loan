package com.example.syndicateloan.workflow;

/**
 * UC-028 step 2〜4: 差し戻された稟議と、新しい回の承認経路の案。
 *
 * @param previous 差し戻された稟議。件名と本文は前の回の値で、案として示す(UC-028 BR-005)。
 *                 段は前の回の段とその判断とコメント(step 2)
 * @param route    今の経路属性で選び直した規則の案から作った、新しい回の承認経路(UC-028 BR-004)
 */
public record ResubmissionProposal(ApprovalView previous, ProposedRoute route) {
}
