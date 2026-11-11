package com.example.syndicateloan.syndicate;

/** 融資案件の状態(Issue #27)。 */
public enum DealStatus {
    APPLIED, SCREENING, PREPARING, FORMING, FORMED, DECLINED, WITHDRAWN;

    /** 謝絶か取下げで終わっている。 */
    public boolean isClosed() {
        return this == DECLINED || this == WITHDRAWN;
    }
}
