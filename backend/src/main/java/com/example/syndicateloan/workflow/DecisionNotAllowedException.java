package com.example.syndicateloan.workflow;

/** UC-025・UC-026 A2: 今は判断できない。何も記録していない。 */
public class DecisionNotAllowedException extends RuntimeException {

    /** 判断できない理由。 */
    public enum Reason {
        /** 稟議が合議中でも承認中でもない。 */
        NOT_IN_PROGRESS,
        /** 利用者が、今の回の判断する段にいない。 */
        NOT_ASSIGNED,
        /** UC-026 BR-001: 自分の段より前の段がまだ承認していない(合議中も含む)。 */
        NOT_YOUR_TURN,
        /** UC-025 BR-006: 自分の段をすでに判断した。 */
        ALREADY_DECIDED
    }

    private final Reason reason;

    public DecisionNotAllowedException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
