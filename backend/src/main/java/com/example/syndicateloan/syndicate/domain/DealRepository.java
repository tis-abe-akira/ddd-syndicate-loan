package com.example.syndicateloan.syndicate.domain;

import com.example.syndicateloan.syndicate.DealForFormation;
import java.util.Optional;

/** 融資案件のポート。 */
public interface DealRepository {

    Optional<DealForFormation> findForFormation(long dealId);

    Optional<DealForFormation> lockForFormation(long dealId);

    /** 組成準備中の融資案件を組成中にする。組成準備中でなければ何も変えず false。 */
    boolean markForming(long dealId);
}
