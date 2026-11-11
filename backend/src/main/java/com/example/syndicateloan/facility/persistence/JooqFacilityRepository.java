package com.example.syndicateloan.facility.persistence;

import static com.example.syndicateloan.facility.persistence.jooq.generated.Tables.FACILITY_;
import static com.example.syndicateloan.facility.persistence.jooq.generated.Tables.FACILITY_SHARE;

import com.example.syndicateloan.facility.domain.DrawdownPeriod;
import com.example.syndicateloan.facility.domain.Facility;
import com.example.syndicateloan.facility.domain.FacilityRepository;
import com.example.syndicateloan.facility.domain.FacilityStatus;
import com.example.syndicateloan.shared.ConcurrentChangeException;
import com.example.syndicateloan.shared.SharePie;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** {@link FacilityRepository} の jOOQ のアダプタ。 */
@Repository
@Transactional(readOnly = true)
public class JooqFacilityRepository implements FacilityRepository {

    private final DSLContext ctx;

    public JooqFacilityRepository(DSLContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public Optional<Facility> findByDealId(long dealId) {
        return ctx.selectFrom(FACILITY_)
                // UC-015 BR-004: A deal has at most one facility.
                .where(FACILITY_.DEAL_ID.eq(dealId))
                .fetchOptional()
                .map(row -> new Facility(
                        row.getId(),
                        row.getDealId(),
                        row.getCurrency(),
                        new DrawdownPeriod(row.getStartDate(), row.getEndDate()),
                        sharePie(row.getId()),
                        FacilityStatus.valueOf(row.getStatus()),
                        row.getApprovalRequestId(),
                        row.getVersion()));
    }

    @Override
    @Transactional
    public Facility insert(Facility facility) {
        long id = ctx.insertInto(FACILITY_)
                .set(FACILITY_.DEAL_ID, facility.dealId())
                .set(FACILITY_.COMMITMENT_AMOUNT, facility.commitmentAmount())
                .set(FACILITY_.CURRENCY, facility.currency())
                .set(FACILITY_.START_DATE, facility.period().startDate())
                .set(FACILITY_.END_DATE, facility.period().endDate())
                .set(FACILITY_.STATUS, facility.status().name())
                .set(FACILITY_.APPROVAL_REQUEST_ID, facility.approvalRequestId())
                .set(FACILITY_.VERSION, 0)
                .returning(FACILITY_.ID)
                .fetchSingle(FACILITY_.ID);
        insertShares(id, facility.sharePie());
        return new Facility(id, facility.dealId(), facility.currency(), facility.period(), facility.sharePie(),
                facility.status(), facility.approvalRequestId(), 0);
    }

    @Override
    @Transactional
    public Facility update(Facility facility) {
        int updated = ctx.update(FACILITY_)
                .set(FACILITY_.COMMITMENT_AMOUNT, facility.commitmentAmount())
                .set(FACILITY_.START_DATE, facility.period().startDate())
                .set(FACILITY_.END_DATE, facility.period().endDate())
                .set(FACILITY_.STATUS, facility.status().name())
                .set(FACILITY_.APPROVAL_REQUEST_ID, facility.approvalRequestId())
                .set(FACILITY_.VERSION, FACILITY_.VERSION.plus(1))
                .where(FACILITY_.ID.eq(facility.id()))
                .and(FACILITY_.VERSION.eq(facility.version()))
                .execute();
        if (updated == 0) {
            throw new ConcurrentChangeException("ファシリティが別の操作で変わりました。");
        }
        ctx.deleteFrom(FACILITY_SHARE).where(FACILITY_SHARE.FACILITY_ID.eq(facility.id())).execute();
        insertShares(facility.id(), facility.sharePie());
        return new Facility(facility.id(), facility.dealId(), facility.currency(), facility.period(),
                facility.sharePie(), facility.status(), facility.approvalRequestId(), facility.version() + 1);
    }

    private SharePie sharePie(long facilityId) {
        Map<Long, BigDecimal> shares = new LinkedHashMap<>();
        ctx.select(FACILITY_SHARE.ORGANIZATION_ID, FACILITY_SHARE.AMOUNT)
                .from(FACILITY_SHARE)
                .where(FACILITY_SHARE.FACILITY_ID.eq(facilityId))
                .orderBy(FACILITY_SHARE.ORGANIZATION_ID)
                .forEach(row -> shares.put(row.value1(), row.value2()));
        return new SharePie(shares);
    }

    private void insertShares(long facilityId, SharePie sharePie) {
        var insert = ctx.insertInto(FACILITY_SHARE,
                FACILITY_SHARE.FACILITY_ID, FACILITY_SHARE.ORGANIZATION_ID, FACILITY_SHARE.AMOUNT);
        for (var share : sharePie.shares().entrySet()) {
            insert = insert.values(facilityId, share.getKey(), share.getValue());
        }
        insert.execute();
    }
}
