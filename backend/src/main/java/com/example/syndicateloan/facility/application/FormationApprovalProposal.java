package com.example.syndicateloan.facility.application;

import com.example.syndicateloan.workflow.ProposedRoute;

/**
 * UC-015 step 7(UC-024 step 4): 組成の稟議の承認経路の案と件名の案。
 *
 * @param facilityId      稟議にかけるファシリティ
 * @param facilityVersion ファシリティのバージョン。申請のときに送り返す
 * @param route           承認経路の案
 */
public record FormationApprovalProposal(long facilityId, int facilityVersion, ProposedRoute route) {
}
