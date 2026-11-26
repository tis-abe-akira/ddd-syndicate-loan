package com.example.syndicateloan.organization;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** 利用者と職位を、他のモジュールに見せる公開 API。 */
public interface Members {

    /** 利用者 id と、その利用者。存在しない id は含まない(無効な利用者は含む)。 */
    Map<Long, Member> findAll(Collection<Long> userIds);

    /** 利用組織の有効な利用者。 */
    List<Member> activeMembers(long organizationId);

    /** 職位 id と、その職位。存在しない id は含まない。 */
    Map<Long, Position> positions(Collection<Long> positionIds);
}
