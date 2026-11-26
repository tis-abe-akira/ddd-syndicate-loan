package com.example.syndicateloan.organization.persistence;

import static com.example.syndicateloan.organization.persistence.jooq.generated.Tables.APP_USER;
import static com.example.syndicateloan.organization.persistence.jooq.generated.Tables.POSITION;

import com.example.syndicateloan.organization.Member;
import com.example.syndicateloan.organization.Position;
import com.example.syndicateloan.organization.domain.MemberRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** {@link MemberRepository} の jOOQ のアダプタ。 */
@Repository
@Transactional(readOnly = true)
public class JooqMemberRepository implements MemberRepository {

    private final DSLContext ctx;

    public JooqMemberRepository(DSLContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public Map<Long, Member> findAll(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return members(APP_USER.ID.in(userIds)).stream()
                .collect(Collectors.toMap(Member::userId, Function.identity()));
    }

    @Override
    public List<Member> findActive(long organizationId) {
        return members(APP_USER.ORGANIZATION_ID.eq(organizationId).and(APP_USER.ACTIVE.isTrue()));
    }

    @Override
    public Map<Long, Position> findPositions(Collection<Long> positionIds) {
        if (positionIds.isEmpty()) {
            return Map.of();
        }
        return ctx.selectFrom(POSITION)
                .where(POSITION.ID.in(positionIds))
                .fetch(row -> new Position(row.getId(), row.getOrganizationId(), row.getName(), row.getRank()))
                .stream()
                .collect(Collectors.toMap(Position::id, Function.identity()));
    }

    private List<Member> members(Condition condition) {
        return ctx.select(APP_USER.ID, APP_USER.ORGANIZATION_ID, APP_USER.NAME, APP_USER.ACTIVE,
                        POSITION.ID, POSITION.ORGANIZATION_ID, POSITION.NAME, POSITION.RANK)
                .from(APP_USER)
                .leftJoin(POSITION).on(POSITION.ID.eq(APP_USER.POSITION_ID))
                .where(condition)
                .orderBy(APP_USER.ID)
                .fetch(JooqMemberRepository::member);
    }

    private static Member member(Record row) {
        Long positionId = row.get(POSITION.ID);
        Position position = positionId == null ? null
                : new Position(positionId, row.get(POSITION.ORGANIZATION_ID), row.get(POSITION.NAME), row.get(POSITION.RANK));
        return new Member(row.get(APP_USER.ID), row.get(APP_USER.ORGANIZATION_ID), row.get(APP_USER.NAME),
                row.get(APP_USER.ACTIVE), position);
    }
}
