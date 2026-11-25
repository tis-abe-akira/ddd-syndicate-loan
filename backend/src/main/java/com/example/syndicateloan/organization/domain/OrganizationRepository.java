package com.example.syndicateloan.organization.domain;

import java.util.Collection;
import java.util.Map;

/** 利用組織のポート。 */
public interface OrganizationRepository {

    Map<Long, String> findNames(Collection<Long> organizationIds);
}
