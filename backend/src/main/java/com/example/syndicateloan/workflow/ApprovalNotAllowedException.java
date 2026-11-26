package com.example.syndicateloan.workflow;

/** UC-024 A1、A2、A3、UC-028 A1、A2、A3: 今は起案(再申請)できない。何も登録していない。 */
public class ApprovalNotAllowedException extends RuntimeException {

    /** 起案できない理由。 */
    public enum Reason {
        /** A1: 同じ対象の稟議が合議中か承認中である。 */
        IN_PROGRESS,
        /** A1: 同じ対象の稟議が差し戻されている。再申請できる(UC-028)。 */
        SENT_BACK,
        /** A2: 同じ対象の最後の稟議が否決されている。再起案として起案する(UC-029)。 */
        REJECTED,
        /** A3: 合う承認経路の規則がない(UC-028 A3 も)。 */
        NO_ROUTE_RULE,
        /** UC-028 A1: 同じ対象に、再申請できる差し戻された稟議がない。 */
        NOT_SENT_BACK,
        /** UC-028 A2: 差し戻された稟議の起案者でない。 */
        NOT_DRAFTER
    }

    private final Reason reason;
    private final Long existingRequestId;

    public ApprovalNotAllowedException(Reason reason, Long existingRequestId, String message) {
        super(message);
        this.reason = reason;
        this.existingRequestId = existingRequestId;
    }

    public Reason reason() {
        return reason;
    }

    /** 理由に当たる稟議(進んでいる・差し戻された・否決された稟議)。当たる稟議がなければ null。 */
    public Long existingRequestId() {
        return existingRequestId;
    }
}
