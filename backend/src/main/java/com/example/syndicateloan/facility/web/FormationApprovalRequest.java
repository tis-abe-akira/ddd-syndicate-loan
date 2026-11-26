package com.example.syndicateloan.facility.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * UC-015 step 7: 組成の稟議の申請。稟議の中身(経路属性・条件)はサーバーがファシリティから組み立てるので、送らない。
 *
 * @param facilityVersion  経路の案と一緒に受け取ったファシリティのバージョン
 * @param routeRuleId      経路の案を出した規則
 * @param routeRuleVersion 規則のバージョン
 * @param title            件名
 * @param body             本文
 * @param steps            起案者が直した承認経路
 */
public record FormationApprovalRequest(
        int facilityVersion,
        long routeRuleId,
        int routeRuleVersion,
        @Schema(types = {"string", "null"}) String title,
        @Schema(types = {"string", "null"}) String body,
        @NotNull @Valid List<StepRequest> steps) {

    /**
     * 承認経路の一つの段に入れた利用者。
     *
     * @param kind   CONSULTATION、MAIN、REPORT
     * @param order  メイントラックの中の順番。メイントラック以外は null
     * @param userId 利用者。空なら null
     */
    public record StepRequest(
            @NotNull String kind,
            @Schema(types = {"integer", "null"}) Integer order,
            @Schema(types = {"integer", "null"}) Long userId) {
    }
}
