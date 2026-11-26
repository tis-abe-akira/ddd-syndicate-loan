package com.example.syndicateloan.workflow;

/** 承認経路の段の種類。 */
public enum StepKind {
    /** 合議先。メイントラックの前に並行して審議する。 */
    CONSULTATION,
    /** メイントラックの承認者。職位の低い順に承認する。 */
    MAIN,
    /** 報告先。最終決裁で承認された稟議を受け取る。 */
    REPORT
}
