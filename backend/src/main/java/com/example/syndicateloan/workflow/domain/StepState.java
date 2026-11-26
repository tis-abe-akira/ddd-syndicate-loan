package com.example.syndicateloan.workflow.domain;

import com.example.syndicateloan.workflow.StepKind;
import java.time.LocalDateTime;

/**
 * 今の回の、承認経路の一つの段と、その判断。
 *
 * @param id         段
 * @param kind       段の種類
 * @param order      メイントラックの中の順番。メイントラック以外は null
 * @param positionId この段に求めた職位。なければ null
 * @param actorId    判断する(報告では受け取る)利用者
 * @param decision   判断
 * @param comment    コメント。なければ null
 * @param decidedAt  判断した日時。なければ null
 */
public record StepState(
        long id,
        StepKind kind,
        Integer order,
        Long positionId,
        long actorId,
        StepDecision decision,
        String comment,
        LocalDateTime decidedAt) {
}
