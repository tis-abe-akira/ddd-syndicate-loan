package com.example.syndicateloan.workflow.domain;

import com.example.syndicateloan.workflow.StepKind;

/**
 * 申請する承認経路の一つの段。
 *
 * @param kind         段の種類
 * @param order        メイントラックの中の順番。メイントラック以外は null
 * @param positionId   この段に求めた職位。なければ null
 * @param actorId      判断する(報告では受け取る)利用者
 * @param autoAssigned 規則の案のまま変えていないか
 */
public record RouteStep(StepKind kind, Integer order, Long positionId, long actorId, boolean autoAssigned) {
}
