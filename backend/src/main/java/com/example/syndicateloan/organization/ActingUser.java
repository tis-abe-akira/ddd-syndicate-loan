package com.example.syndicateloan.organization;

import java.util.Objects;
import java.util.Set;

/**
 * いま操作している利用者。業務のモジュールは、利用者の組織とロールで操作を許すかを決める(NFR-007)。
 *
 * @param userId         利用者
 * @param organizationId 利用者の所属する利用組織
 * @param roles          利用者のロール(USER_ROLE.role)
 */
public record ActingUser(long userId, long organizationId, Set<String> roles) {

    /** 金融機関の業務ユーザー。 */
    public static final String BUSINESS_USER = "BUSINESS_USER";

    public ActingUser {
        roles = Set.copyOf(Objects.requireNonNull(roles, "roles"));
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    public boolean belongsTo(long organizationId) {
        return this.organizationId == organizationId;
    }
}
