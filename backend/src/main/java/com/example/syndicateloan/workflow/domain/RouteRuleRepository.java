package com.example.syndicateloan.workflow.domain;

import java.util.List;

/** 承認経路の規則のポート。 */
public interface RouteRuleRepository {

    /** 利用組織の、稟議の種類が同じ規則。 */
    List<RouteRule> findBySubjectType(long organizationId, String subjectType);
}
