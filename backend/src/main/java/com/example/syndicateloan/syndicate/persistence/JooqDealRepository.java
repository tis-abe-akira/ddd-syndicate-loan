package com.example.syndicateloan.syndicate.persistence;

import static com.example.syndicateloan.syndicate.persistence.jooq.generated.Tables.DEAL;
import static com.example.syndicateloan.syndicate.persistence.jooq.generated.Tables.DEAL_PARTICIPANT;

import com.example.syndicateloan.syndicate.AcceptedInvestor;
import com.example.syndicateloan.syndicate.DealForFormation;
import com.example.syndicateloan.syndicate.DealStatus;
import com.example.syndicateloan.syndicate.domain.DealRepository;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Records;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** {@link DealRepository} の jOOQ のアダプタ。 */
@Repository
@Transactional(readOnly = true)
public class JooqDealRepository implements DealRepository {

    private final DSLContext ctx;

    public JooqDealRepository(DSLContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public Optional<DealForFormation> findForFormation(long dealId) {
        return read(dealId, false);
    }

    @Override
    @Transactional
    public Optional<DealForFormation> lockForFormation(long dealId) {
        return read(dealId, true);
    }

    private Optional<DealForFormation> read(long dealId, boolean lock) {
        var query = ctx.select(DEAL.ID, DEAL.VERSION, DEAL.LEAD_ORGANIZATION_ID, DEAL.CURRENCY, DEAL.STATUS)
                .from(DEAL)
                .where(DEAL.ID.eq(dealId));
        var deal = lock ? query.forUpdate().fetchOptional() : query.fetchOptional();
        return deal.map(row -> new DealForFormation(
                row.value1(),
                row.value2(),
                row.value3(),
                row.value4(),
                DealStatus.valueOf(row.value5()),
                acceptedInvestors(dealId)));
    }

    private List<AcceptedInvestor> acceptedInvestors(long dealId) {
        return ctx.select(DEAL_PARTICIPANT.ORGANIZATION_ID, DEAL_PARTICIPANT.COMMITMENT_AMOUNT)
                .from(DEAL_PARTICIPANT)
                .where(DEAL_PARTICIPANT.DEAL_ID.eq(dealId))
                // UC-015 BR-001: only investors who accepted the invitation enter the share pie.
                .and(DEAL_PARTICIPANT.ROLE.eq("INVESTOR"))
                .and(DEAL_PARTICIPANT.STATUS.eq("ACCEPTED"))
                .orderBy(DEAL_PARTICIPANT.ORGANIZATION_ID)
                .fetch(Records.mapping(AcceptedInvestor::new));
    }
}
