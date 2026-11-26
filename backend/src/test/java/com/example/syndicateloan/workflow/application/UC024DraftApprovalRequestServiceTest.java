package com.example.syndicateloan.workflow.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.example.syndicateloan.organization.ActingUser;
import com.example.syndicateloan.usecase.UseCase;
import com.example.syndicateloan.workflow.ApprovalConcurrentChangeException;
import com.example.syndicateloan.workflow.ApprovalDraft;
import com.example.syndicateloan.workflow.ApprovalDraft.StepAssignment;
import com.example.syndicateloan.workflow.ApprovalNotAllowedException;
import com.example.syndicateloan.workflow.ApprovalNotAllowedException.Reason;
import com.example.syndicateloan.workflow.ApprovalRequests;
import com.example.syndicateloan.workflow.ApprovalStatus;
import com.example.syndicateloan.workflow.ApprovalSubject;
import com.example.syndicateloan.workflow.InvalidApprovalDraftException;
import com.example.syndicateloan.workflow.InvalidApprovalDraftException.DraftError;
import com.example.syndicateloan.workflow.ProposedRoute;
import com.example.syndicateloan.workflow.ProposedRoute.Candidate;
import com.example.syndicateloan.workflow.ProposedRoute.ProposedStep;
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
 * UC-024 稟議を起案する: サービスの層のテスト(この use case の結合層、Layers: Service)。
 *
 * <ul>
 *   <li>公開 API {@link ApprovalRequests} → jOOQ → Testcontainers の PostgreSQL。お知らせ(notification)も本物。</li>
 *   <li>テストデータは {@code src/test/resources/db/migration/R__uc024_draft_approval_request.sql}。</li>
 *   <li>各テストはトランザクションの中で動き、終わるとロールバックする。申請の日時は 2026-12-01 09:00(日本時間)。</li>
 *   <li>拒否の文言は仕様が定めていないので検証しない。理由の種類と、直すべき項目(field)を検証する。</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Import(UC024DraftApprovalRequestServiceTest.Config.class)
class UC024DraftApprovalRequestServiceTest {

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

    // テストデータ(R__uc024_draft_approval_request.sql)
    private static final ActingUser DRAFTER = new ActingUser(7101, 7001, Set.of("BUSINESS_USER"));
    private static final long MANAGER_A = 7102; // 課長、7501/7502 の1段目の既定
    private static final long MANAGER_B = 7103; // 課長
    private static final long DIRECTOR_A = 7104; // 部長
    private static final long DIRECTOR_B = 7105; // 部長
    private static final long HEAD = 7106; // 本部長、7502 の3段目の既定
    private static final long REVIEWER = 7107; // 合議の既定(課長)
    private static final long REPORTER = 7108; // 報告の既定(職位なし)
    private static final long RETIRED = 7109; // 無効な部長
    private static final long OTHER_BANK = 7110; // 他行の部長
    private static final long NO_POSITION = 7111; // 職位なし
    private static final BigDecimal UNDER_ONE_BILLION = new BigDecimal("999999999");
    private static final BigDecimal ONE_BILLION = new BigDecimal("1000000000");

    @Autowired
    private ApprovalRequests approvals;

    @Autowired
    private JdbcTemplate jdbc;

    // ---------------------------------------------------------------- Main success scenario

    @Test
    @UseCase(id = "UC-024", businessRules = {"BR-001", "BR-002"})
    void proposes_the_route_of_the_matching_rule_with_the_title_proposal() {
        ProposedRoute route = approvals.propose(subject("deal:1", UNDER_ONE_BILLION), DRAFTER);

        assertThat(route.routeRuleId()).isEqualTo(7501);
        assertThat(route.title()).isEqualTo("UC024 件名の案");
        assertThat(route.steps()).extracting(ProposedStep::kind, ProposedStep::order, ProposedStep::userId)
                .containsExactly(
                        tuple(StepKind.CONSULTATION, null, REVIEWER),
                        tuple(StepKind.MAIN, 1, MANAGER_A),
                        tuple(StepKind.MAIN, 2, null),
                        tuple(StepKind.REPORT, null, REPORTER));
        // step 4 does not register anything
        assertThat(count("workflow.approval_request")).isZero();
    }

    @Test
    @UseCase(id = "UC-024", requirements = {"NFR-009"})
    void submits_the_request_in_consultation_and_records_everything_it_carries() {
        ApprovalSubject subject = subject("deal:1", new BigDecimal("1800000000"));
        ProposedRoute route = approvals.propose(subject, DRAFTER);

        SubmittedApproval submitted = approvals.submit(draft(subject, route, "件名", "本文", List.of(
                consult(REVIEWER), main(1, MANAGER_A), main(2, DIRECTOR_B), main(3, HEAD), report(REPORTER))), DRAFTER);

        assertThat(submitted.status()).isEqualTo(ApprovalStatus.IN_CONSULTATION);
        Map<String, Object> request = jdbc.queryForMap("select * from workflow.approval_request where id = ?",
                submitted.requestId());
        assertThat(request).containsEntry("organization_id", 7001L)
                .containsEntry("subject_type", "UC024_TYPE")
                .containsEntry("subject_ref", "deal:1")
                .containsEntry("title", "件名")
                .containsEntry("body", "本文")
                .containsEntry("drafter_id", 7101L)
                .containsEntry("route_rule_id", 7502L)
                .containsEntry("status", "IN_CONSULTATION")
                .containsEntry("round", 1)
                .containsEntry("submitted_at", Timestamp.valueOf(LocalDateTime.of(2026, 12, 1, 9, 0)));
        assertThat(jdbc.queryForList("select name, value from workflow.approval_attribute where request_id = ?",
                submitted.requestId()))
                .extracting(row -> row.get("name") + "=" + ((BigDecimal) row.get("value")).stripTrailingZeros().toPlainString())
                .containsExactlyInAnyOrder("amount=1800000000", "credit_score=72");
        assertThat(jdbc.queryForList("select name, value from workflow.approval_condition where request_id = ?",
                submitted.requestId()))
                .extracting(row -> row.get("name") + "=" + row.get("value"))
                .containsExactly("currency=JPY");
        // every step of round 1 waits for its decision
        assertThat(jdbc.queryForList("""
                select kind, step_order, actor_id, round, decision from workflow.approval_step
                where request_id = ? order by id""", submitted.requestId()))
                .extracting(row -> row.get("kind") + ":" + row.get("step_order") + ":" + row.get("actor_id") + ":"
                        + row.get("round") + ":" + row.get("decision"))
                .containsExactly(
                        "CONSULTATION:null:7107:1:PENDING",
                        "MAIN:1:7102:1:PENDING",
                        "MAIN:2:7105:1:PENDING",
                        "MAIN:3:7106:1:PENDING",
                        "REPORT:null:7108:1:PENDING");
    }

    @Test
    @UseCase(id = "UC-024", businessRules = {"BR-008"})
    void sends_the_consultation_request_to_every_consultee_and_nothing_to_the_others() {
        ApprovalSubject subject = subject("deal:1", UNDER_ONE_BILLION);
        ProposedRoute route = approvals.propose(subject, DRAFTER);

        SubmittedApproval submitted = approvals.submit(draft(subject, route, List.of(
                consult(REVIEWER), consult(MANAGER_B), main(1, MANAGER_A), main(2, DIRECTOR_A), report(REPORTER))),
                DRAFTER);

        assertThat(notices()).containsExactlyInAnyOrder(
                "7107:CONSULTATION_REQUESTED:approval-request:" + submitted.requestId(),
                "7103:CONSULTATION_REQUESTED:approval-request:" + submitted.requestId());
    }

    @Test
    @UseCase(id = "UC-024", businessRules = {"BR-008"})
    void puts_a_request_without_consultees_in_approval_and_asks_only_the_first_approver() {
        ApprovalSubject subject = new ApprovalSubject("UC024_NOCONSULT", "deal:1", "件名", Map.of(), Map.of());
        ProposedRoute route = approvals.propose(subject, DRAFTER);

        SubmittedApproval submitted = approvals.submit(draft(subject, route, List.of(main(1, MANAGER_A))), DRAFTER);

        assertThat(submitted.status()).isEqualTo(ApprovalStatus.IN_APPROVAL);
        assertThat(notices()).containsExactly("7102:APPROVAL_REQUESTED:approval-request:" + submitted.requestId());
    }

    // ---------------------------------------------------------------- BR-001: 規則の選び方

    @Test
    @UseCase(id = "UC-024", businessRules = {"BR-001"})
    void includes_the_lower_bound_of_a_condition() {
        assertThat(approvals.propose(subject("deal:1", ONE_BILLION), DRAFTER).routeRuleId()).isEqualTo(7502);
    }

    @Test
    @UseCase(id = "UC-024", businessRules = {"BR-001"})
    void excludes_the_upper_bound_of_a_condition() {
        assertThat(approvals.propose(subject("deal:1", UNDER_ONE_BILLION), DRAFTER).routeRuleId()).isEqualTo(7501);
    }

    @Test
    @UseCase(id = "UC-024", businessRules = {"BR-001"})
    void chooses_the_rule_with_the_smallest_priority_when_several_match() {
        ApprovalSubject subject = new ApprovalSubject("UC024_PRIO", "deal:1", "件名",
                Map.of("amount", BigDecimal.ONE, "credit_score", new BigDecimal("80")), Map.of());

        assertThat(approvals.propose(subject, DRAFTER).routeRuleId()).isEqualTo(7504);
    }

    @Test
    @UseCase(id = "UC-024", businessRules = {"BR-001"})
    void does_not_match_a_rule_whose_attribute_is_missing() {
        ApprovalSubject subject = new ApprovalSubject("UC024_PRIO", "deal:1", "件名",
                Map.of("amount", BigDecimal.ONE), Map.of());

        assertThat(approvals.propose(subject, DRAFTER).routeRuleId()).isEqualTo(7503);
    }

    @Test
    @UseCase(id = "UC-024", businessRules = {"BR-001"}, requirements = {"NFR-007"})
    void uses_only_the_rules_of_the_drafters_organization() {
        // 他行の同じ種類の規則 7507 は優先順位が1でも選ばれない
        assertThat(approvals.propose(subject("deal:1", UNDER_ONE_BILLION), DRAFTER).routeRuleId()).isEqualTo(7501);
    }

    // ---------------------------------------------------------------- A1〜A3

    @Test
    @UseCase(id = "UC-024", scenario = "A1: 同じ対象の稟議が進んでいる", businessRules = {"BR-009"})
    void refuses_a_new_request_while_one_for_the_same_subject_is_in_progress() {
        long existing = submitDefault("deal:1");

        ApprovalNotAllowedException e = catchThrowableOfType(ApprovalNotAllowedException.class,
                () -> approvals.propose(subject("deal:1", UNDER_ONE_BILLION), DRAFTER));

        assertThat(e.reason()).isEqualTo(Reason.IN_PROGRESS);
        assertThat(e.existingRequestId()).isEqualTo(existing);
    }

    @Test
    @UseCase(id = "UC-024", scenario = "A1: 同じ対象の稟議が進んでいる", businessRules = {"BR-009"})
    void points_to_the_sent_back_request_of_the_same_subject() {
        long existing = submitDefault("deal:1");
        jdbc.update("update workflow.approval_request set status = 'DRAFT' where id = ?", existing);

        ApprovalNotAllowedException e = catchThrowableOfType(ApprovalNotAllowedException.class,
                () -> approvals.propose(subject("deal:1", UNDER_ONE_BILLION), DRAFTER));

        assertThat(e.reason()).isEqualTo(Reason.SENT_BACK);
        assertThat(e.existingRequestId()).isEqualTo(existing);
    }

    @Test
    @UseCase(id = "UC-024", businessRules = {"BR-009"})
    void allows_a_request_for_another_subject_while_one_is_in_progress() {
        submitDefault("deal:1");

        assertThat(submitDefault("deal:2")).isPositive();
    }

    @Test
    @UseCase(id = "UC-024", scenario = "A2: 同じ対象の稟議が否決されている", businessRules = {"BR-009"})
    void sends_the_drafter_to_redraft_when_the_last_request_was_rejected() {
        long rejected = submitDefault("deal:1");
        jdbc.update("update workflow.approval_request set status = 'REJECTED', decided_at = now() where id = ?",
                rejected);

        ApprovalNotAllowedException e = catchThrowableOfType(ApprovalNotAllowedException.class,
                () -> approvals.propose(subject("deal:1", UNDER_ONE_BILLION), DRAFTER));

        assertThat(e.reason()).isEqualTo(Reason.REJECTED);
        assertThat(e.existingRequestId()).isEqualTo(rejected);
    }

    @Test
    @UseCase(id = "UC-024", scenario = "A3: 合う承認経路の規則がない", businessRules = {"BR-001"})
    void refuses_to_draft_when_no_rule_matches() {
        ApprovalSubject subject = new ApprovalSubject("UC024_PRIO", "deal:1", "件名",
                Map.of("credit_score", new BigDecimal("79")), Map.of());

        ApprovalNotAllowedException e = catchThrowableOfType(ApprovalNotAllowedException.class,
                () -> approvals.propose(subject, DRAFTER));

        assertThat(e.reason()).isEqualTo(Reason.NO_ROUTE_RULE);
        assertThat(count("workflow.approval_request")).isZero();
    }

    // ---------------------------------------------------------------- A4、BR-002、BR-003

    @Test
    @UseCase(id = "UC-024", scenario = "A4: 段の承認者が決まらない", businessRules = {"BR-002", "BR-003"})
    void leaves_a_step_without_default_user_empty_and_offers_the_users_between_its_position_and_the_next() {
        ProposedRoute route = approvals.propose(subject("deal:1", ONE_BILLION), DRAFTER);

        ProposedStep director = route.steps().get(2);
        assertThat(director.order()).isEqualTo(2);
        assertThat(director.userId()).isNull();
        // 部長以上で本部長より低い、有効な同じ利用組織の利用者(退職者・他行・本部長は入らない)
        assertThat(director.candidates()).extracting(Candidate::userId).containsExactlyInAnyOrder(DIRECTOR_A, DIRECTOR_B);
    }

    @Test
    @UseCase(id = "UC-024", scenario = "A4: 段の承認者が決まらない", businessRules = {"BR-003"})
    void offers_any_higher_position_for_the_final_approval_step() {
        ProposedRoute route = approvals.propose(subject("deal:1", UNDER_ONE_BILLION), DRAFTER);

        assertThat(route.steps().get(2).candidates()).extracting(Candidate::userId)
                .containsExactlyInAnyOrder(DIRECTOR_A, DIRECTOR_B, HEAD);
    }

    @Test
    @UseCase(id = "UC-024", businessRules = {"BR-003"})
    void records_which_steps_the_drafter_replaced() {
        ApprovalSubject subject = subject("deal:1", UNDER_ONE_BILLION);
        ProposedRoute route = approvals.propose(subject, DRAFTER);

        SubmittedApproval submitted = approvals.submit(draft(subject, route, List.of(
                consult(REVIEWER), main(1, MANAGER_B), main(2, HEAD), report(REPORTER))), DRAFTER);

        assertThat(submitted.steps()).extracting(SubmittedApproval.Step::userId, SubmittedApproval.Step::autoAssigned)
                .containsExactly(tuple(REVIEWER, true), tuple(MANAGER_B, false), tuple(HEAD, false),
                        tuple(REPORTER, true));
    }

    @Test
    @UseCase(id = "UC-024", businessRules = {"BR-003"})
    void lets_the_drafter_add_and_remove_consultees_and_reports() {
        ApprovalSubject subject = subject("deal:1", UNDER_ONE_BILLION);
        ProposedRoute route = approvals.propose(subject, DRAFTER);

        SubmittedApproval submitted = approvals.submit(draft(subject, route, List.of(
                consult(NO_POSITION), main(1, MANAGER_A), main(2, DIRECTOR_A))), DRAFTER);

        assertThat(submitted.steps()).extracting(SubmittedApproval.Step::kind, SubmittedApproval.Step::userId,
                SubmittedApproval.Step::autoAssigned)
                .containsExactly(tuple(StepKind.CONSULTATION, NO_POSITION, false),
                        tuple(StepKind.MAIN, MANAGER_A, true), tuple(StepKind.MAIN, DIRECTOR_A, false));
    }

    @Test
    @UseCase(id = "UC-024", scenario = "A5: 承認経路か件名が正しくない", businessRules = {"BR-003"})
    void rejects_a_main_step_below_its_position() {
        assertInvalid(List.of(consult(REVIEWER), main(1, NO_POSITION), main(2, DIRECTOR_A)), "steps[1]");
    }

    @Test
    @UseCase(id = "UC-024", scenario = "A5: 承認経路か件名が正しくない", businessRules = {"BR-003"})
    void rejects_a_main_step_at_or_above_the_position_of_the_next_step() {
        assertInvalid(List.of(consult(REVIEWER), main(1, DIRECTOR_A), main(2, DIRECTOR_B)), "steps[1]");
    }

    @Test
    @UseCase(id = "UC-024", scenario = "A5: 承認経路か件名が正しくない", businessRules = {"BR-003"})
    void rejects_a_removed_main_step() {
        assertInvalid(List.of(consult(REVIEWER), main(1, MANAGER_A)), "steps");
    }

    @Test
    @UseCase(id = "UC-024", scenario = "A5: 承認経路か件名が正しくない", businessRules = {"BR-003"})
    void rejects_an_added_main_step() {
        assertInvalid(List.of(main(1, MANAGER_A), main(2, DIRECTOR_A), main(3, HEAD)), "steps");
    }

    @Test
    @UseCase(id = "UC-024", scenario = "A5: 承認経路か件名が正しくない", businessRules = {"BR-003"})
    void rejects_reordered_main_steps() {
        assertInvalid(List.of(main(2, DIRECTOR_A), main(1, MANAGER_A)), "steps");
    }

    // ---------------------------------------------------------------- A5: BR-004〜BR-006 と件名

    @Test
    @UseCase(id = "UC-024", scenario = "A5: 承認経路か件名が正しくない", businessRules = {"BR-002"})
    void rejects_an_empty_step() {
        assertInvalid(List.of(consult(REVIEWER), main(1, MANAGER_A), main(2, null)), "steps[2]");
    }

    @Test
    @UseCase(id = "UC-024", scenario = "A5: 承認経路か件名が正しくない", businessRules = {"BR-004"})
    void rejects_the_drafter_as_a_consultee() {
        assertInvalid(List.of(consult(DRAFTER.userId()), main(1, MANAGER_A), main(2, DIRECTOR_A)), "steps[0]");
    }

    @Test
    @UseCase(id = "UC-024", scenario = "A5: 承認経路か件名が正しくない", businessRules = {"BR-004"})
    void rejects_the_drafter_as_an_approver() {
        ActingUser director = new ActingUser(DIRECTOR_A, 7001, Set.of("BUSINESS_USER"));
        ApprovalSubject subject = subject("deal:1", UNDER_ONE_BILLION);
        ProposedRoute route = approvals.propose(subject, director);

        InvalidApprovalDraftException e = catchThrowableOfType(InvalidApprovalDraftException.class,
                () -> approvals.submit(draft(subject, route, List.of(main(1, MANAGER_A), main(2, DIRECTOR_A))),
                        director));

        assertThat(e.errors()).extracting(DraftError::field).containsExactly("steps[1]");
    }

    @Test
    @UseCase(id = "UC-024", businessRules = {"BR-004"})
    void lets_the_drafter_receive_the_report() {
        assertThat(submit(List.of(main(1, MANAGER_A), main(2, DIRECTOR_A), report(DRAFTER.userId())))).isPositive();
    }

    @Test
    @UseCase(id = "UC-024", scenario = "A5: 承認経路か件名が正しくない", businessRules = {"BR-005"})
    void rejects_the_same_user_in_two_deciding_steps() {
        assertInvalid(List.of(consult(MANAGER_A), main(1, MANAGER_A), main(2, DIRECTOR_A)), "steps[1]");
    }

    @Test
    @UseCase(id = "UC-024", businessRules = {"BR-005"})
    void lets_a_report_overlap_a_deciding_step() {
        assertThat(submit(List.of(main(1, MANAGER_A), main(2, DIRECTOR_A), report(MANAGER_A)))).isPositive();
    }

    @Test
    @UseCase(id = "UC-024", scenario = "A5: 承認経路か件名が正しくない", businessRules = {"BR-006"},
            requirements = {"NFR-007"})
    void rejects_a_user_of_another_organization() {
        assertInvalid(List.of(consult(OTHER_BANK), main(1, MANAGER_A), main(2, DIRECTOR_A)), "steps[0]");
    }

    @Test
    @UseCase(id = "UC-024", scenario = "A5: 承認経路か件名が正しくない", businessRules = {"BR-006"})
    void rejects_an_inactive_user() {
        assertInvalid(List.of(consult(REVIEWER), main(1, MANAGER_A), main(2, RETIRED)), "steps[2]");
    }

    @Test
    @UseCase(id = "UC-024", scenario = "A5: 承認経路か件名が正しくない", businessRules = {"BR-006"})
    void rejects_a_route_without_a_main_step() {
        ApprovalSubject subject = new ApprovalSubject("UC024_NOMAIN", "deal:1", "件名", Map.of(), Map.of());
        ProposedRoute route = approvals.propose(subject, DRAFTER);

        InvalidApprovalDraftException e = catchThrowableOfType(InvalidApprovalDraftException.class,
                () -> approvals.submit(draft(subject, route, List.of(consult(REVIEWER))), DRAFTER));

        assertThat(e.errors()).extracting(DraftError::field).contains("steps");
        assertThat(count("workflow.approval_request")).isZero();
    }

    @Test
    @UseCase(id = "UC-024", scenario = "A5: 承認経路か件名が正しくない")
    void rejects_an_empty_title_and_shows_every_field_to_correct() {
        ApprovalSubject subject = subject("deal:1", UNDER_ONE_BILLION);
        ProposedRoute route = approvals.propose(subject, DRAFTER);

        InvalidApprovalDraftException e = catchThrowableOfType(InvalidApprovalDraftException.class,
                () -> approvals.submit(draft(subject, route, " ", null,
                        List.of(consult(REVIEWER), main(1, MANAGER_A), main(2, null))), DRAFTER));

        assertThat(e.errors()).extracting(DraftError::field).containsExactlyInAnyOrder("title", "steps[2]");
        // 失敗時の事後条件: 稟議も承認経路もお知らせも残らない
        assertThat(count("workflow.approval_request")).isZero();
        assertThat(count("workflow.approval_step")).isZero();
        assertThat(count("notification.notice")).isZero();
    }

    // ---------------------------------------------------------------- A6

    @Test
    @UseCase(id = "UC-024", scenario = "A6: 起案の間に規則か同じ対象の稟議が変わった", requirements = {"NFR-003"})
    void registers_nothing_when_the_rule_changed_after_drafting_started() {
        ApprovalSubject subject = subject("deal:1", UNDER_ONE_BILLION);
        ProposedRoute route = approvals.propose(subject, DRAFTER);
        jdbc.update("update workflow.route_rule set version = version + 1 where id = 7501");

        assertThatThrownBy(() -> approvals.submit(draft(subject, route, defaultSteps()), DRAFTER))
                .isInstanceOf(ApprovalConcurrentChangeException.class);
        assertThat(count("workflow.approval_request")).isZero();
        assertThat(count("notification.notice")).isZero();
    }

    @Test
    @UseCase(id = "UC-024", scenario = "A6: 起案の間に規則か同じ対象の稟議が変わった",
            businessRules = {"BR-009"}, requirements = {"NFR-003"})
    void registers_nothing_when_another_request_for_the_subject_was_submitted_after_drafting_started() {
        ApprovalSubject subject = subject("deal:1", UNDER_ONE_BILLION);
        ProposedRoute route = approvals.propose(subject, DRAFTER);
        long other = submitDefault("deal:1"); // 別の起案者の操作で、先に申請された

        assertThatThrownBy(() -> approvals.submit(draft(subject, route, defaultSteps()), DRAFTER))
                .isInstanceOf(ApprovalConcurrentChangeException.class);
        assertThat(jdbc.queryForObject("select count(*) from workflow.approval_request where subject_ref = 'deal:1'",
                Integer.class)).isEqualTo(1);
        assertThat(other).isPositive();
    }

    // ---------------------------------------------------------------- helpers

    private static ApprovalSubject subject(String subjectRef, BigDecimal amount) {
        return new ApprovalSubject("UC024_TYPE", subjectRef, "UC024 件名の案",
                Map.of("amount", amount, "credit_score", new BigDecimal("72")), Map.of("currency", "JPY"));
    }

    private static ApprovalDraft draft(ApprovalSubject subject, ProposedRoute route, List<StepAssignment> steps) {
        return draft(subject, route, route.title(), null, steps);
    }

    private static ApprovalDraft draft(ApprovalSubject subject, ProposedRoute route, String title, String body,
            List<StepAssignment> steps) {
        return new ApprovalDraft(subject, route.routeRuleId(), route.routeRuleVersion(), title, body, steps);
    }

    private static List<StepAssignment> defaultSteps() {
        return List.of(consult(REVIEWER), main(1, MANAGER_A), main(2, DIRECTOR_A), report(REPORTER));
    }

    /** 10億円未満(規則 7501)の稟議を、既定どおりの承認経路で申請する。 */
    private long submitDefault(String subjectRef) {
        ApprovalSubject subject = subject(subjectRef, UNDER_ONE_BILLION);
        return approvals.submit(draft(subject, approvals.propose(subject, DRAFTER), defaultSteps()), DRAFTER)
                .requestId();
    }

    private long submit(List<StepAssignment> steps) {
        ApprovalSubject subject = subject("deal:1", UNDER_ONE_BILLION);
        return approvals.submit(draft(subject, approvals.propose(subject, DRAFTER), steps), DRAFTER).requestId();
    }

    /** 10億円未満の稟議を steps で申請して A5 になり、fields だけが直すべき項目で、何も残らないことを確かめる。 */
    private void assertInvalid(List<StepAssignment> steps, String... fields) {
        ApprovalSubject subject = subject("deal:1", UNDER_ONE_BILLION);
        ProposedRoute route = approvals.propose(subject, DRAFTER);

        InvalidApprovalDraftException e = catchThrowableOfType(InvalidApprovalDraftException.class,
                () -> approvals.submit(draft(subject, route, steps), DRAFTER));

        assertThat(e).as("UC-024 A5").isNotNull();
        assertThat(e.errors()).extracting(DraftError::field).containsExactlyInAnyOrder(fields);
        assertThat(count("workflow.approval_request")).isZero();
        assertThat(count("notification.notice")).isZero();
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

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }

    private List<String> notices() {
        List<String> notices = new ArrayList<>();
        jdbc.query("select recipient_id, kind, subject_ref from notification.notice order by id",
                rs -> {
                    notices.add(rs.getLong(1) + ":" + rs.getString(2) + ":" + rs.getString(3));
                });
        return notices;
    }

    private static org.assertj.core.groups.Tuple tuple(Object... values) {
        return org.assertj.core.groups.Tuple.tuple(values);
    }
}
