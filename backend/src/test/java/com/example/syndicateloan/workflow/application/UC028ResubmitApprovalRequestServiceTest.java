package com.example.syndicateloan.workflow.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.tuple;

import com.example.syndicateloan.organization.ActingUser;
import com.example.syndicateloan.usecase.UseCase;
import com.example.syndicateloan.workflow.ApprovalConcurrentChangeException;
import com.example.syndicateloan.workflow.ApprovalDecisions;
import com.example.syndicateloan.workflow.ApprovalDraft.StepAssignment;
import com.example.syndicateloan.workflow.ApprovalForbiddenException;
import com.example.syndicateloan.workflow.ApprovalNotAllowedException;
import com.example.syndicateloan.workflow.ApprovalNotAllowedException.Reason;
import com.example.syndicateloan.workflow.ApprovalRequests;
import com.example.syndicateloan.workflow.ApprovalStatus;
import com.example.syndicateloan.workflow.ApprovalSubject;
import com.example.syndicateloan.workflow.ApprovalView;
import com.example.syndicateloan.workflow.Decision;
import com.example.syndicateloan.workflow.DecisionCommand;
import com.example.syndicateloan.workflow.InvalidApprovalDraftException;
import com.example.syndicateloan.workflow.InvalidApprovalDraftException.DraftError;
import com.example.syndicateloan.workflow.ProposedRoute.Candidate;
import com.example.syndicateloan.workflow.ProposedRoute.ProposedStep;
import com.example.syndicateloan.workflow.Resubmission;
import com.example.syndicateloan.workflow.ResubmissionProposal;
import com.example.syndicateloan.workflow.StepKind;
import com.example.syndicateloan.workflow.SubmittedApproval;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * UC-028 差し戻された稟議を再申請する: サービスの層のテスト(この use case の結合層、Layers: Service)。
 *
 * <ul>
 *   <li>公開 API {@link ApprovalRequests} → jOOQ → Testcontainers の PostgreSQL。お知らせ(notification)も本物。</li>
 *   <li>テストデータは {@code src/test/resources/db/migration/R__uc028_resubmit_approval_request.sql}。</li>
 *   <li>各テストはトランザクションの中で動き、終わるとロールバックする。再申請の日時は 2026-12-01 09:00(日本時間)。</li>
 *   <li>拒否の文言は仕様が定めていないので検証しない。理由の種類と、直すべき項目(field)を検証する。</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Import(UC028ResubmitApprovalRequestServiceTest.Config.class)
class UC028ResubmitApprovalRequestServiceTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class Config {

        @Bean
        @ServiceConnection
        PostgreSQLContainer postgres() {
            return new PostgreSQLContainer("postgres:17-alpine");
        }

        @Bean
        @Primary
        Clock fixedClock() {
            ZoneId tokyo = ZoneId.of("Asia/Tokyo");
            return Clock.fixed(ZonedDateTime.of(2026, 12, 1, 9, 0, 0, 0, tokyo).toInstant(), tokyo);
        }
    }

    // テストデータ(R__uc028_resubmit_approval_request.sql)
    private static final ActingUser DRAFTER = new ActingUser(8101, 8001, Set.of("BUSINESS_USER"));
    private static final ActingUser ADMIN = new ActingUser(8108, 8001, Set.of("ORG_ADMIN"));
    private static final long REVIEWER = 8103; // 合議の既定。回 1 で承認した
    private static final long MANAGER = 8104; // 課長。回 1 で差し戻した
    private static final long DIRECTOR_A = 8105; // 部長
    private static final long REPORTER = 8106; // 報告の既定
    private static final long DIRECTOR_B = 8107; // 部長
    private static final long SENT_BACK = 8201; // 差し戻された稟議(deal:1、version 2)
    private static final BigDecimal UNDER_100M = new BigDecimal("60000000");
    private static final BigDecimal OVER_100M = new BigDecimal("200000000");

    @Autowired
    private ApprovalRequests approvals;

    @Autowired
    private ApprovalDecisions decisions;

    @Autowired
    private JdbcTemplate jdbc;

    // ---------------------------------------------------------------- Main success scenario

    @Test
    @UseCase(id = "UC-028", scenario = "Main Success Scenario", businessRules = {"BR-004", "BR-005"})
    void shows_the_sent_back_request_with_the_previous_decisions_and_a_new_route() {
        ResubmissionProposal proposal = approvals.proposeResubmission(subject("deal:1", UNDER_100M), DRAFTER);

        // step 2: 差し戻された稟議と、前の回の各段の判断とコメント
        ApprovalView previous = proposal.previous();
        assertThat(previous.id()).isEqualTo(SENT_BACK);
        assertThat(previous.status()).isEqualTo(ApprovalStatus.DRAFT);
        assertThat(previous.round()).isEqualTo(1);
        assertThat(previous.steps()).extracting(ApprovalView.Step::kind, ApprovalView.Step::actorId,
                        ApprovalView.Step::decision, ApprovalView.Step::comment)
                .containsExactly(
                        tuple(StepKind.CONSULTATION, REVIEWER, "APPROVED", "異議なし"),
                        tuple(StepKind.MAIN, MANAGER, "SENT_BACK", "金額の根拠を足してください"),
                        tuple(StepKind.REPORT, REPORTER, "PENDING", null));
        // step 4、BR-005: 件名と本文の案は前の回の値(業務の件名の案は使わない)
        assertThat(previous.title()).isEqualTo("前の件名");
        assertThat(previous.body()).isEqualTo("前の本文");
        assertThat(proposal.route().title()).isEqualTo("前の件名");
        // step 3、4、BR-004: 今の経路属性で選んだ規則の案
        assertThat(proposal.route().routeRuleId()).isEqualTo(8501);
        assertThat(proposal.route().steps()).extracting(ProposedStep::kind, ProposedStep::order, ProposedStep::userId)
                .containsExactly(
                        tuple(StepKind.CONSULTATION, null, REVIEWER),
                        tuple(StepKind.MAIN, 1, MANAGER),
                        tuple(StepKind.REPORT, null, REPORTER));
        // 再申請を始めただけでは何も変わらない(A7 もこれで満たす)
        assertUnchanged();
    }

    @Test
    @UseCase(id = "UC-028", scenario = "Main Success Scenario",
            businessRules = {"BR-003", "BR-005", "BR-006", "BR-007", "BR-009"}, requirements = {"NFR-009"})
    void resubmits_the_request_as_a_new_round_in_consultation_and_keeps_the_previous_round() {
        ApprovalSubject subject = subject("deal:1", UNDER_100M);
        ResubmissionProposal proposal = approvals.proposeResubmission(subject, DRAFTER);

        SubmittedApproval submitted = approvals.resubmit(resubmission(subject, proposal, "直した件名", "直した本文",
                List.of(consult(REVIEWER), main(1, MANAGER), report(REPORTER))), DRAFTER);

        assertThat(submitted.requestId()).isEqualTo(SENT_BACK);
        assertThat(submitted.status()).isEqualTo(ApprovalStatus.IN_CONSULTATION);
        // step 8: 申請の回を1増やし、件名・本文・規則・再申請の日時を記録する。種類・対象・起案者は変わらない
        Map<String, Object> request = jdbc.queryForMap("select * from workflow.approval_request where id = ?",
                SENT_BACK);
        assertThat(request).containsEntry("round", 2)
                .containsEntry("status", "IN_CONSULTATION")
                .containsEntry("title", "直した件名")
                .containsEntry("body", "直した本文")
                .containsEntry("route_rule_id", 8501L)
                .containsEntry("subject_type", "UC028_TYPE")
                .containsEntry("subject_ref", "deal:1")
                .containsEntry("drafter_id", 8101L)
                .containsEntry("submitted_at", Timestamp.valueOf(LocalDateTime.of(2026, 12, 1, 9, 0)))
                .containsEntry("decided_at", null);
        // BR-005: 経路属性と稟議の条件は、業務が決めた今の値
        assertThat(jdbc.queryForObject("select value from workflow.approval_attribute where request_id = ?"
                + " and name = 'amount'", BigDecimal.class, SENT_BACK)).isEqualByComparingTo(UNDER_100M);
        assertThat(jdbc.queryForList("select name || '=' || value from workflow.approval_condition"
                + " where request_id = ? order by name", String.class, SENT_BACK))
                .containsExactly("currency=JPY", "term_months=48");
        // BR-003: 新しい回の段はすべて判断待ち。前の回で承認した合議先も判断し直す
        assertThat(steps(2)).containsExactly(
                "CONSULTATION:8103:PENDING", "MAIN:8104:PENDING", "REPORT:8106:PENDING");
        // BR-006: 前の回の段と判断とコメントはそのまま
        assertThat(steps(1)).containsExactly(
                "CONSULTATION:8103:APPROVED:異議なし", "MAIN:8104:SENT_BACK:金額の根拠を足してください",
                "REPORT:8106:PENDING");
        // step 9、BR-007: 合議先の全員に判断の依頼。報告先には知らせない
        assertThat(notices()).containsExactly("8103:CONSULTATION_REQUESTED:approval-request:" + SENT_BACK);
    }

    @Test
    @UseCase(id = "UC-028", scenario = "Main Success Scenario", businessRules = {"BR-004", "BR-007"})
    void chooses_the_rule_again_for_the_changed_amount_and_starts_in_approval_without_consultees() {
        ApprovalSubject subject = subject("deal:1", OVER_100M);
        ResubmissionProposal proposal = approvals.proposeResubmission(subject, DRAFTER);

        // BR-004: 前の回の経路(8501)を引き継がず、1億円以上の規則 8502 を選び直す
        assertThat(proposal.route().routeRuleId()).isEqualTo(8502);
        SubmittedApproval submitted = approvals.resubmit(resubmission(subject, proposal, "前の件名", null,
                List.of(main(1, MANAGER), main(2, DIRECTOR_A))), DRAFTER);

        assertThat(submitted.status()).isEqualTo(ApprovalStatus.IN_APPROVAL);
        assertThat(steps(2)).containsExactly("MAIN:8104:PENDING", "MAIN:8105:PENDING");
        // UC-024 BR-008: 合議先がいなければ、メイントラックの最初の承認者だけに依頼する
        assertThat(notices()).containsExactly("8104:APPROVAL_REQUESTED:approval-request:" + SENT_BACK);
    }

    @Test
    @UseCase(id = "UC-028", scenario = "Main Success Scenario", businessRules = {"BR-003"},
            requirements = {"NFR-003"})
    void does_not_take_a_decision_made_on_the_previous_round() {
        int openedBefore = decisions.view(SENT_BACK, consultee()).version(); // 回 1 のときに開いた(2)
        ApprovalSubject subject = subject("deal:1", UNDER_100M);
        approvals.resubmit(resubmission(subject, approvals.proposeResubmission(subject, DRAFTER), "直した件名", null,
                List.of(consult(REVIEWER), main(1, MANAGER), report(REPORTER))), DRAFTER);

        // BR-003(UC-025 A6): 前の回に開いた稟議のままでは、新しい回の判断を記録しない
        assertThatThrownBy(() -> decisions.decide(SENT_BACK, new DecisionCommand(Decision.APPROVE, null,
                openedBefore), consultee())).isInstanceOf(ApprovalConcurrentChangeException.class);
        assertThat(steps(2)).first().isEqualTo("CONSULTATION:8103:PENDING");

        // 開き直せば、新しい回で判断し直せる
        int reopened = decisions.view(SENT_BACK, consultee()).version();
        assertThat(decisions.decide(SENT_BACK, new DecisionCommand(Decision.APPROVE, null, reopened), consultee())
                .status()).isEqualTo(ApprovalStatus.IN_APPROVAL);
    }

    // ---------------------------------------------------------------- A1、A2、A3

    @Test
    @UseCase(id = "UC-028", scenario = "A1: 差し戻された稟議がない", businessRules = {"BR-001"})
    void points_to_redrafting_when_the_request_was_rejected() {
        ApprovalNotAllowedException e = catchThrowableOfType(ApprovalNotAllowedException.class,
                () -> approvals.proposeResubmission(subject("deal:2", UNDER_100M), DRAFTER));

        assertThat(e).isNotNull();
        assertThat(e.reason()).isEqualTo(Reason.REJECTED);
        assertThat(e.existingRequestId()).isEqualTo(8202L);
    }

    @Test
    @UseCase(id = "UC-028", scenario = "A1: 差し戻された稟議がない", businessRules = {"BR-001"})
    void refuses_to_resubmit_a_request_in_approval_or_a_subject_without_requests() {
        ApprovalNotAllowedException inApproval = catchThrowableOfType(ApprovalNotAllowedException.class,
                () -> approvals.proposeResubmission(subject("deal:3", UNDER_100M), DRAFTER));
        assertThat(inApproval.reason()).isEqualTo(Reason.NOT_SENT_BACK);

        ApprovalNotAllowedException none = catchThrowableOfType(ApprovalNotAllowedException.class,
                () -> approvals.proposeResubmission(subject("deal:9", UNDER_100M), DRAFTER));
        assertThat(none.reason()).isEqualTo(Reason.NOT_SENT_BACK);
        assertThat(none.existingRequestId()).isNull();
    }

    @Test
    @UseCase(id = "UC-028", scenario = "A1: 差し戻された稟議がない", requirements = {"NFR-007"})
    void does_not_reveal_a_sent_back_request_of_another_organization() {
        // deal:5 には他行(8002)の差し戻された稟議 8205 があるが、起案者の利用組織には無い
        ApprovalNotAllowedException e = catchThrowableOfType(ApprovalNotAllowedException.class,
                () -> approvals.proposeResubmission(subject("deal:5", UNDER_100M), DRAFTER));

        assertThat(e.reason()).isEqualTo(Reason.NOT_SENT_BACK);
        assertThat(e.existingRequestId()).isNull();
    }

    @Test
    @UseCase(id = "UC-028", scenario = "A2: 起案者が前の回の起案者でない", businessRules = {"BR-002"})
    void lets_only_the_drafter_of_the_sent_back_request_resubmit_it() {
        ApprovalNotAllowedException proposing = catchThrowableOfType(ApprovalNotAllowedException.class,
                () -> approvals.proposeResubmission(subject("deal:6", UNDER_100M), DRAFTER));
        assertThat(proposing.reason()).isEqualTo(Reason.NOT_DRAFTER);
        assertThat(proposing.existingRequestId()).isEqualTo(8206L);

        // 再申請しようとしても拒み、何も変わらない
        ApprovalSubject subject = subject("deal:6", UNDER_100M);
        ApprovalNotAllowedException resubmitting = catchThrowableOfType(ApprovalNotAllowedException.class,
                () -> approvals.resubmit(new Resubmission(subject, 8206, 1, 8501, 0, "件名", null,
                        List.of(consult(REVIEWER), main(1, MANAGER), report(REPORTER))), DRAFTER));
        assertThat(resubmitting.reason()).isEqualTo(Reason.NOT_DRAFTER);
        assertThat(jdbc.queryForObject("select round from workflow.approval_request where id = 8206", Integer.class))
                .isEqualTo(1);
    }

    @Test
    @UseCase(id = "UC-028", scenario = "A3: 合う承認経路の規則がない", businessRules = {"BR-004"})
    void refuses_to_resubmit_when_no_rule_matches_the_changed_attributes() {
        ApprovalNotAllowedException e = catchThrowableOfType(ApprovalNotAllowedException.class,
                () -> approvals.proposeResubmission(new ApprovalSubject("UC028_LIMITED", "deal:4", "業務の件名の案",
                        Map.of("amount", OVER_100M), Map.of()), DRAFTER));

        assertThat(e.reason()).isEqualTo(Reason.NO_ROUTE_RULE);
    }

    // ---------------------------------------------------------------- A4、A5

    @Test
    @UseCase(id = "UC-028", scenario = "A4: 段の承認者が決まらない", businessRules = {"BR-004"})
    void leaves_a_step_without_default_user_empty_and_offers_candidates() {
        ResubmissionProposal proposal = approvals.proposeResubmission(subject("deal:1", OVER_100M), DRAFTER);

        ProposedStep director = proposal.route().steps().get(1);
        assertThat(director.order()).isEqualTo(2);
        assertThat(director.userId()).isNull();
        // UC-024 BR-003: 最終決裁の段には、求める職位(部長)以上の利用者を差し替えられる
        assertThat(director.candidates()).extracting(Candidate::userId).containsExactlyInAnyOrder(DIRECTOR_A,
                DIRECTOR_B);
    }

    @Test
    @UseCase(id = "UC-028", scenario = "A5: 承認経路か件名が正しくない", businessRules = {"BR-004"})
    void rejects_an_empty_title_and_the_drafter_as_an_approver_and_records_nothing() {
        ApprovalSubject subject = subject("deal:1", UNDER_100M);
        ResubmissionProposal proposal = approvals.proposeResubmission(subject, DRAFTER);

        InvalidApprovalDraftException e = catchThrowableOfType(InvalidApprovalDraftException.class,
                () -> approvals.resubmit(resubmission(subject, proposal, " ", null,
                        List.of(consult(DRAFTER.userId()), main(1, MANAGER), report(REPORTER))), DRAFTER));

        assertThat(e).isNotNull();
        // UC-024 BR-004: 起案者は自分の稟議の合議先になれない
        assertThat(e.errors()).extracting(DraftError::field).containsExactlyInAnyOrder("title", "steps[0]");
        assertUnchanged();
    }

    // ---------------------------------------------------------------- A6、前提条件

    @Test
    @UseCase(id = "UC-028", scenario = "A6: 再申請の間に稟議か規則が変わった", requirements = {"NFR-003"})
    void records_nothing_when_the_request_changed_after_it_was_shown() {
        ApprovalSubject subject = subject("deal:1", UNDER_100M);
        ResubmissionProposal proposal = approvals.proposeResubmission(subject, DRAFTER);
        jdbc.update("update workflow.approval_request set version = version + 1 where id = ?", SENT_BACK);

        assertThatThrownBy(() -> approvals.resubmit(resubmission(subject, proposal, "直した件名", null,
                List.of(consult(REVIEWER), main(1, MANAGER), report(REPORTER))), DRAFTER))
                .isInstanceOf(ApprovalConcurrentChangeException.class);
        assertThat(steps(2)).isEmpty();
        assertThat(notices()).isEmpty();
    }

    @Test
    @UseCase(id = "UC-028", scenario = "A6: 再申請の間に稟議か規則が変わった", businessRules = {"BR-008"},
            requirements = {"NFR-003"})
    void records_nothing_when_the_request_was_resubmitted_by_another_operation() {
        ApprovalSubject subject = subject("deal:1", UNDER_100M);
        ResubmissionProposal proposal = approvals.proposeResubmission(subject, DRAFTER);
        List<StepAssignment> route = List.of(consult(REVIEWER), main(1, MANAGER), report(REPORTER));
        approvals.resubmit(resubmission(subject, proposal, "別の画面から", null, route), DRAFTER);

        // 同じ案のまま、もう一度再申請する: 稟議は差し戻された状態でなくなった
        assertThatThrownBy(() -> approvals.resubmit(resubmission(subject, proposal, "直した件名", null, route),
                DRAFTER)).isInstanceOf(ApprovalConcurrentChangeException.class);
        assertThat(jdbc.queryForObject("select round from workflow.approval_request where id = ?", Integer.class,
                SENT_BACK)).isEqualTo(2);
    }

    @Test
    @UseCase(id = "UC-028", scenario = "A6: 再申請の間に稟議か規則が変わった", requirements = {"NFR-003"})
    void records_nothing_when_the_rule_changed_after_it_was_shown() {
        ApprovalSubject subject = subject("deal:1", UNDER_100M);
        ResubmissionProposal proposal = approvals.proposeResubmission(subject, DRAFTER);
        jdbc.update("update workflow.route_rule set version = version + 1 where id = 8501");

        assertThatThrownBy(() -> approvals.resubmit(resubmission(subject, proposal, "直した件名", null,
                List.of(consult(REVIEWER), main(1, MANAGER), report(REPORTER))), DRAFTER))
                .isInstanceOf(ApprovalConcurrentChangeException.class);
        assertUnchanged();
    }

    @Test
    @UseCase(id = "UC-028", scenario = "Main Success Scenario")
    void lets_only_business_users_resubmit() {
        ApprovalSubject subject = subject("deal:1", UNDER_100M);
        assertThatThrownBy(() -> approvals.proposeResubmission(subject, ADMIN))
                .isInstanceOf(ApprovalForbiddenException.class);
        assertThatThrownBy(() -> approvals.resubmit(new Resubmission(subject, SENT_BACK, 2, 8501, 0, "件名", null,
                List.of(consult(REVIEWER), main(1, MANAGER), report(REPORTER))), ADMIN))
                .isInstanceOf(ApprovalForbiddenException.class);
        assertUnchanged();
    }

    // ---------------------------------------------------------------- helpers

    /** 直した後の稟議の中身。件名の案は、UC-028 では使われないことを確かめるため前の回と違う値にする。 */
    private static ApprovalSubject subject(String subjectRef, BigDecimal amount) {
        return new ApprovalSubject("UC028_TYPE", subjectRef, "業務の件名の案", Map.of("amount", amount),
                Map.of("currency", "JPY", "term_months", "48"));
    }

    private static Resubmission resubmission(ApprovalSubject subject, ResubmissionProposal proposal, String title,
            String body, List<StepAssignment> steps) {
        return new Resubmission(subject, proposal.previous().id(), proposal.previous().version(),
                proposal.route().routeRuleId(), proposal.route().routeRuleVersion(), title, body, steps);
    }

    private static ActingUser consultee() {
        return new ActingUser(REVIEWER, 8001, Set.of("BUSINESS_USER"));
    }

    private static StepAssignment consult(Long userId) {
        return new StepAssignment(StepKind.CONSULTATION, null, userId);
    }

    private static StepAssignment main(int order, Long userId) {
        return new StepAssignment(StepKind.MAIN, order, userId);
    }

    private static StepAssignment report(Long userId) {
        return new StepAssignment(StepKind.REPORT, null, userId);
    }

    /** 差し戻された稟議 8201 が、差し戻されたまま、回も段もお知らせも変わっていない(Failure Postconditions)。 */
    private void assertUnchanged() {
        Map<String, Object> request = jdbc.queryForMap(
                "select status, round, title, version from workflow.approval_request where id = ?", SENT_BACK);
        assertThat(request).containsEntry("status", "DRAFT").containsEntry("round", 1)
                .containsEntry("title", "前の件名").containsEntry("version", 2);
        assertThat(steps(2)).isEmpty();
        assertThat(notices()).isEmpty();
    }

    /** 稟議 8201 の、その回の段を「種類:利用者:判断(:コメント)」で、段の順に。 */
    private List<String> steps(int round) {
        return jdbc.query("select kind, actor_id, decision, comment from workflow.approval_step"
                        + " where request_id = ? and round = ? order by id",
                (rs, n) -> rs.getString(1) + ":" + rs.getLong(2) + ":" + rs.getString(3)
                        + (rs.getString(4) == null ? "" : ":" + rs.getString(4)),
                SENT_BACK, round);
    }

    /** この利用組織(8001)の利用者に届いたお知らせを「受け取る利用者:種類:対象」で、届いた順に。 */
    private List<String> notices() {
        List<String> notices = new ArrayList<>();
        jdbc.query("select n.recipient_id, n.kind, n.subject_ref from notification.notice n"
                        + " join organization.app_user u on u.id = n.recipient_id where u.organization_id = 8001"
                        + " order by n.id",
                rs -> {
                    notices.add(rs.getLong(1) + ":" + rs.getString(2) + ":" + rs.getString(3));
                });
        return notices;
    }
}
