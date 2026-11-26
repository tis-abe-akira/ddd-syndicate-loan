package com.example.syndicateloan.organization.domain;

import com.example.syndicateloan.organization.Member;
import com.example.syndicateloan.organization.Position;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** 利用者と職位のポート。 */
public interface MemberRepository {

    Map<Long, Member> findAll(Collection<Long> userIds);

    List<Member> findActive(long organizationId);

    Map<Long, Position> findPositions(Collection<Long> positionIds);
}
