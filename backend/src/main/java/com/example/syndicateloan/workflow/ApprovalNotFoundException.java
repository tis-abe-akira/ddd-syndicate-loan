package com.example.syndicateloan.workflow;

/** UC-025・UC-026 A1: 稟議が存在しないか、利用者の利用組織の稟議でない(NFR-007)。 */
public class ApprovalNotFoundException extends RuntimeException {

    public ApprovalNotFoundException(long requestId) {
        super("稟議が見つかりません: " + requestId);
    }
}
