package com.example.syndicateloan.workflow;

/** 稟議の結果。業務に伝える(UC-025 BR-008、UC-026 BR-008)。 */
public enum ApprovalOutcome {
    /** 最終決裁で承認された。 */
    APPROVED,
    /** 差し戻された。起案者は直して再申請できる。 */
    SENT_BACK,
    /** 否決された。条件を変えた再起案だけができる。 */
    REJECTED
}
