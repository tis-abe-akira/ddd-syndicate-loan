package com.example.syndicateloan.organization.application;

import com.example.syndicateloan.organization.Member;
import com.example.syndicateloan.organization.Members;
import com.example.syndicateloan.organization.Position;
import com.example.syndicateloan.organization.domain.MemberRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link Members} の実装。 */
@Service
@Transactional(readOnly = true)
public class MembersService implements Members {

    private final MemberRepository members;

    public MembersService(MemberRepository members) {
        this.members = members;
    }

    @Override
    public Map<Long, Member> findAll(Collection<Long> userIds) {
        return members.findAll(userIds);
    }

    @Override
    public List<Member> activeMembers(long organizationId) {
        return members.findActive(organizationId);
    }

    @Override
    public Map<Long, Position> positions(Collection<Long> positionIds) {
        return members.findPositions(positionIds);
    }
}
