package com.example.syndicateloan.syndicate;

import java.util.Optional;

/** 融資案件を、他のモジュールに見せる公開 API。 */
public interface Deals {

    /** UC-015: ファシリティの組成に要る情報。融資案件がなければ空。 */
    Optional<DealForFormation> findForFormation(long dealId);

    /**
     * UC-015: 同じ情報を、融資案件の行とその参加者の行をロックして読む。
     * 呼び出し側のトランザクションが終わるまで、融資案件と参加者は他の操作で変わらない。
     */
    Optional<DealForFormation> lockForFormation(long dealId);

    /**
     * UC-015 step 8、BR-010: 組成の稟議を起案した融資案件を、組成準備中から組成中にする。
     *
     * @throws IllegalStateException 融資案件が組成準備中でないとき
     */
    void startFormation(long dealId);
}
