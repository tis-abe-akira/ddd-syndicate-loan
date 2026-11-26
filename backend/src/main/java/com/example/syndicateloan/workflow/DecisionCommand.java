package com.example.syndicateloan.workflow;

/**
 * UC-025・UC-026 step 3、4: 合議先・承認者の判断。
 *
 * @param decision      判断
 * @param comment       コメント。差し戻しと否決では必須(UC-025 BR-005)
 * @param viewedVersion 稟議を開いたときのバージョン(A6 の判定に使う)
 */
public record DecisionCommand(Decision decision, String comment, int viewedVersion) {
}
