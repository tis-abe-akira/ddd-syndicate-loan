package com.example.syndicateloan.syndicate.application;

import com.example.syndicateloan.syndicate.DealForFormation;
import com.example.syndicateloan.syndicate.Deals;
import com.example.syndicateloan.syndicate.domain.DealRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link Deals} の実装。融資案件の情報を他のモジュールに渡す。 */
@Service
@Transactional(readOnly = true)
public class DealsService implements Deals {

    private final DealRepository deals;

    public DealsService(DealRepository deals) {
        this.deals = deals;
    }

    @Override
    public Optional<DealForFormation> findForFormation(long dealId) {
        return deals.findForFormation(dealId);
    }

    @Override
    @Transactional
    public Optional<DealForFormation> lockForFormation(long dealId) {
        return deals.lockForFormation(dealId);
    }
}
