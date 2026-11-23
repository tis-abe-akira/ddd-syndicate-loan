package com.example.syndicateloan.organization.persistence;

import static com.example.syndicateloan.organization.persistence.jooq.generated.Tables.APP_USER;
import static com.example.syndicateloan.organization.persistence.jooq.generated.Tables.USER_ROLE;

import com.example.syndicateloan.organization.ActingUser;
import com.example.syndicateloan.organization.domain.AppUserRepository;
import java.util.HashSet;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** {@link AppUserRepository} の jOOQ のアダプタ。 */
@Repository
@Transactional(readOnly = true)
public class JooqAppUserRepository implements AppUserRepository {

    private final DSLContext ctx;

    public JooqAppUserRepository(DSLContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public Optional<ActingUser> findActiveUser(long userId) {
        return ctx.select(APP_USER.ID, APP_USER.ORGANIZATION_ID)
                .from(APP_USER)
                .where(APP_USER.ID.eq(userId))
                .and(APP_USER.ACTIVE.isTrue())
                .fetchOptional()
                .map(user -> new ActingUser(user.value1(), user.value2(), new HashSet<>(ctx
                        .select(USER_ROLE.ROLE)
                        .from(USER_ROLE)
                        .where(USER_ROLE.USER_ID.eq(userId))
                        .fetch(USER_ROLE.ROLE))));
    }
}
