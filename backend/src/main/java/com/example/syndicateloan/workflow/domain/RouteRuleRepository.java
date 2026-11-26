package com.example.syndicateloan.workflow.domain;

import java.util.List;

/** 承認経路の規則のポート。 */
public interface RouteRuleRepository {

    /** 利用組織の、稟議の種類が同じ規則。 */
    List<RouteRule> findBySubjectType(long organizationId, String subjectType);

    /**
     * 同じ規則を、行を共有ロックして読む。呼び出し側のトランザクションが終わるまで、規則は変わらない
     * (UC-024 A6、NFR-003)。
     */
    List<RouteRule> lockBySubjectType(long organizationId, String subjectType);
}
