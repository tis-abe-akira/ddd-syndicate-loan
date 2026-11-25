package com.example.syndicateloan.organization.persistence;

import static com.example.syndicateloan.organization.persistence.jooq.generated.Tables.ORGANIZATION_;

import com.example.syndicateloan.organization.domain.OrganizationRepository;
import java.util.Collection;
import java.util.Map;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** {@link OrganizationRepository} の jOOQ のアダプタ。 */
@Repository
@Transactional(readOnly = true)
public class JooqOrganizationRepository implements OrganizationRepository {

    private final DSLContext ctx;

    public JooqOrganizationRepository(DSLContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public Map<Long, String> findNames(Collection<Long> organizationIds) {
        return ctx.select(ORGANIZATION_.ID, ORGANIZATION_.NAME)
                .from(ORGANIZATION_)
                .where(ORGANIZATION_.ID.in(organizationIds))
                .fetchMap(ORGANIZATION_.ID, ORGANIZATION_.NAME);
    }
}
