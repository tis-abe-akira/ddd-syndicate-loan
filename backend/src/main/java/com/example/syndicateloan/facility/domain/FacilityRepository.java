package com.example.syndicateloan.facility.domain;

import java.util.Optional;

/** ファシリティのポート。 */
public interface FacilityRepository {

    Optional<Facility> findByDealId(long dealId);

    /** 組成中のファシリティを登録し、id と version を付けて返す。 */
    Facility insert(Facility facility);

    /**
     * ファシリティとそのシェアパイを更新する。読んだ後に別の操作が変えていたら何も変えない。
     *
     * @throws com.example.syndicateloan.shared.ConcurrentChangeException 別の操作が変えていたとき
     */
    Facility update(Facility facility);
}
