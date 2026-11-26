package com.example.syndicateloan.workflow;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * UC-025・UC-026 step 2: 稟議の中身と、承認経路の各段とその判断。
 *
 * @param id             稟議
 * @param title          件名
 * @param body           本文。なければ null
 * @param status         状態
 * @param round          申請の回数
 * @param version        バージョン(判断のときに送り返す)
 * @param drafterId      起案者
 * @param drafterName    起案者の氏名
 * @param subjectType    稟議の種類
 * @param subjectRef     稟議の対象
 * @param attributes     経路属性
 * @param conditions     稟議の条件
 * @param steps          今の回の承認経路
 * @param blockedReason  見ている利用者が今は判断できない理由。判断できるなら null
 */
public record ApprovalView(
        long id,
        String title,
        String body,
        ApprovalStatus status,
        int round,
        int version,
        long drafterId,
        String drafterName,
        String subjectType,
        String subjectRef,
        Map<String, BigDecimal> attributes,
        Map<String, String> conditions,
        List<Step> steps,
        DecisionNotAllowedException.Reason blockedReason) {

    public ApprovalView {
        attributes = Map.copyOf(attributes);
        conditions = Map.copyOf(conditions);
        steps = List.copyOf(steps);
    }

    /**
     * 承認経路の一つの段。
     *
     * @param kind         段の種類
     * @param order        メイントラックの中の順番。メイントラック以外は null
     * @param positionName この段に求めた職位。なければ null
     * @param actorId      判断する(報告では受け取る)利用者
     * @param actorName    その氏名
     * @param decision     判断(PENDING、APPROVED、SENT_BACK、REJECTED、NOTIFIED、SKIPPED)
     * @param comment      コメント。なければ null
     * @param decidedAt    判断した日時。なければ null
     */
    public record Step(
            StepKind kind,
            Integer order,
            String positionName,
            long actorId,
            String actorName,
            String decision,
            String comment,
            LocalDateTime decidedAt) {
    }
}
