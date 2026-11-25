package com.example.syndicateloan.organization.domain;

import java.util.Set;

/**
 * 有効な利用者と、その所属とロール。
 *
 * @param userId           利用者
 * @param name             氏名
 * @param organizationId   所属する利用組織
 * @param organizationName 所属する利用組織の会社名
 * @param roles            ロール
 */
public record ActiveUser(long userId, String name, long organizationId, String organizationName, Set<String> roles) {

    public ActiveUser {
        roles = Set.copyOf(roles);
    }
}
