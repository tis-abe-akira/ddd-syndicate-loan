package com.example.syndicateloan.facility.application;

import com.example.syndicateloan.workflow.ApprovalDraft.StepAssignment;
import java.util.List;

/**
 * UC-015 step 7: 担当者が申請する組成の稟議。稟議の中身(種類・対象・経路属性・条件)は含まない。
 * それはサーバーがファシリティから組み立てる(UC-024 BR-007)。
 *
 * @param dealId           融資案件
 * @param facilityVersion  経路の案を受け取ったときのファシリティのバージョン(NFR-003)
 * @param routeRuleId      経路の案を出した規則
 * @param routeRuleVersion 規則のバージョン
 * @param title            件名
 * @param body             本文。なければ null
 * @param steps            起案者が直した承認経路
 */
public record FormationApprovalCommand(
        long dealId,
        int facilityVersion,
        long routeRuleId,
        int routeRuleVersion,
        String title,
        String body,
        List<StepAssignment> steps) {

    public FormationApprovalCommand {
        steps = List.copyOf(steps);
    }
}
