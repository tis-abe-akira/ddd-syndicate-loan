package com.example.syndicateloan.syndicate.domain;

import com.example.syndicateloan.syndicate.DealForFormation;
import java.util.Optional;

/** 融資案件のポート。 */
public interface DealRepository {

    Optional<DealForFormation> findForFormation(long dealId);

    Optional<DealForFormation> lockForFormation(long dealId);
}
