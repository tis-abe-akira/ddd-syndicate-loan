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

    @Override
    @Transactional
    public void startFormation(long dealId) {
        if (!deals.markForming(dealId)) {
            throw new IllegalStateException("The deal is not preparing: " + dealId);
        }
    }

    @Override
    @Transactional
    public void completeFormation(long dealId) {
        // UC-015 BR-010: an approved formation request makes the deal formed.
        if (!deals.transition(dealId, com.example.syndicateloan.syndicate.DealStatus.FORMING,
                com.example.syndicateloan.syndicate.DealStatus.FORMED)) {
            throw new IllegalStateException("The deal is not forming: " + dealId);
        }
    }

    @Override
    @Transactional
    public void returnToPreparing(long dealId) {
        // UC-015 BR-010: a rejected or sent-back formation request returns the deal to preparing.
        if (!deals.transition(dealId, com.example.syndicateloan.syndicate.DealStatus.FORMING,
                com.example.syndicateloan.syndicate.DealStatus.PREPARING)) {
            throw new IllegalStateException("The deal is not forming: " + dealId);
        }
    }
}
