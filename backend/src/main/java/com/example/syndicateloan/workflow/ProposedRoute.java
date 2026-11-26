package com.example.syndicateloan.workflow;

import java.util.List;

/**
 * UC-024 step 4: 規則の案から作った承認経路と件名の案。
 *
 * @param routeRuleId      経路の案を出した規則
 * @param routeRuleVersion 規則のバージョン。申請のときに送り返す(A6)
 * @param title            件名の案
 * @param steps            承認経路の段(合議先、メイントラックの順、報告先)
 */
public record ProposedRoute(long routeRuleId, int routeRuleVersion, String title, List<ProposedStep> steps) {

    public ProposedRoute {
        steps = List.copyOf(steps);
    }

    /**
     * 承認経路の一つの段。
     *
     * @param kind         段の種類
     * @param order        メイントラックの中の順番。メイントラック以外は null
     * @param positionId   この段に求める職位。なければ null
     * @param positionName この段に求める職位の呼び方。なければ null
     * @param userId       規則が自動で入れた利用者。決まらなければ null(A4)
     * @param candidates   この段に入れられる利用者(BR-003)
     */
    public record ProposedStep(
            StepKind kind,
            Integer order,
            Long positionId,
            String positionName,
            Long userId,
            List<Candidate> candidates) {

        public ProposedStep {
            candidates = List.copyOf(candidates);
        }
    }

    /**
     * 段に入れられる利用者。
     *
     * @param userId       利用者
     * @param name         氏名
     * @param positionName 職位の呼び方。職位を持たなければ null
     */
    public record Candidate(long userId, String name, String positionName) {
    }
}
