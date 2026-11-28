package com.example.syndicateloan.workflow.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.syndicateloan.usecase.UseCase;
import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * UC-026 稟議を承認する: API の層のテスト(結合層)。組成の稟議の結果がファシリティと融資案件に伝わること
 * (UC-015 BR-006・BR-010)と、業務が処理できないとき(A8)も確かめる。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Import(UC026ApproveApprovalRequestApiTest.Config.class)
class UC026ApproveApprovalRequestApiTest {

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

    // テストデータ(R__uc025_uc026_decide_approval_request.sql)
    static final long DRAFTER = 6101;
    static final long CONSULT_A = 6102;
    static final long CONSULT_B = 6103;
    static final long MAIN_1 = 6104;
    static final long MAIN_2 = 6105;
    static final long REPORTER = 6106;
    static final long ORG_ADMIN = 6107;
    static final long OTHER_BANK = 6110;
    static final long INVESTOR = 6111;

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    // ---------------------------------------------------------------- Main success scenario

    @Test
    @UseCase(id = "UC-026", scenario = "Main Success Scenario", businessRules = {"BR-001", "BR-009"})
    void asks_the_next_approver_after_a_non_final_approval() {
        // step 2: 合議先の判断とコメントを含めて示す
        var opened = assertThat(open(6202, MAIN_1)).bodyJson();
        opened.extractingPath("$.steps[0].decision").asString().isEqualTo("APPROVED");
        opened.extractingPath("$.steps[0].comment").asString().isEqualTo("異議なし");
        opened.extractingPath("$.blockedReason").isNull();

        MvcTestResult result = decide(6202, MAIN_1, "APPROVE", null, 1);

        assertThat(result).hasStatusOk();
        assertThat(status(6202)).isEqualTo("IN_APPROVAL");
        assertThat(steps(6202)).containsExactly("6311:APPROVED", "6312:APPROVED", "6313:PENDING", "6314:PENDING");
        assertThat(notices()).containsExactly("6105:APPROVAL_REQUESTED");
    }

    @Test
    @UseCase(id = "UC-026", scenario = "Main Success Scenario", businessRules = {"BR-002", "BR-009", "BR-010"})
    void approves_the_request_at_the_final_approval_and_reports_it() {
        decide(6202, MAIN_1, "APPROVE", null, 1);

        MvcTestResult result = decide(6202, MAIN_2, "APPROVE", "承認します", 2);

        var json = assertThat(result).bodyJson();
        json.extractingPath("$.status").asString().isEqualTo("APPROVED");
        // step 8: 記録した判断を示す。BR-010: 判断・コメント・日時を記録する
        json.extractingPath("$.steps[2].decision").asString().isEqualTo("APPROVED");
        json.extractingPath("$.steps[2].comment").asString().isEqualTo("承認します");
        json.extractingPath("$.steps[2].decidedAt").asString().isEqualTo("2026-12-01T09:00:00");
        assertThat(jdbc.queryForMap("select comment, decided_at from workflow.approval_step where id = 6313"))
                .containsEntry("comment", "承認します")
                .containsEntry("decided_at", java.sql.Timestamp.valueOf("2026-12-01 09:00:00"));
        assertThat(jdbc.queryForObject("select decided_at from workflow.approval_request where id = 6202",
                java.sql.Timestamp.class)).isEqualTo(java.sql.Timestamp.valueOf("2026-12-01 09:00:00"));
        assertThat(steps(6202)).containsExactly("6311:APPROVED", "6312:APPROVED", "6313:APPROVED", "6314:NOTIFIED");
        // 報告先の全員と起案者にお知らせが届く
        assertThat(notices()).containsExactly("6105:APPROVAL_REQUESTED", "6106:REPORTED", "6101:REPORTED");
    }

    @Test
    @UseCase(id = "UC-026", scenario = "A2: 今は判断できない", businessRules = {"BR-001", "BR-007"})
    void does_not_let_a_later_approver_decide_before_the_earlier_one() {
        assertThat(open(6202, MAIN_2)).bodyJson().extractingPath("$.blockedReason").asString().isEqualTo("NOT_YOUR_TURN");
        MvcTestResult result = decide(6202, MAIN_2, "APPROVE", null, 1);

        assertProblem(result, 409, "/problems/decision-not-allowed");
        assertThat(result).bodyJson().extractingPath("$.reason").asString().isEqualTo("NOT_YOUR_TURN");
        assertThat(steps(6202)).containsExactly("6311:APPROVED", "6312:PENDING", "6313:PENDING", "6314:PENDING");
    }

    // ---------------------------------------------------------------- A3〜A6

    @Test
    @UseCase(id = "UC-026", scenario = "A3: 承認者が差し戻す", businessRules = {"BR-003", "BR-009"})
    void sends_the_request_back_from_the_main_track() {
        MvcTestResult result = decide(6202, MAIN_1, "SEND_BACK", "根拠を足してください", 1);

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.status").asString().isEqualTo("DRAFT");
        assertThat(result).bodyJson().extractingPath("$.steps[1].decision").asString().isEqualTo("SENT_BACK");
        assertThat(result).bodyJson().extractingPath("$.steps[1].comment").asString().isEqualTo("根拠を足してください");

        assertThat(status(6202)).isEqualTo("DRAFT");
        assertThat(steps(6202)).containsExactly("6311:APPROVED", "6312:SENT_BACK", "6313:SKIPPED", "6314:PENDING");
        assertThat(notices()).containsExactly("6101:SENT_BACK");
    }

    @Test
    @UseCase(id = "UC-026", scenario = "A4: 承認者が否決する", businessRules = {"BR-004", "BR-009"})
    void rejects_the_request_from_the_main_track() {
        MvcTestResult result = decide(6202, MAIN_1, "REJECT", "見送ります", 1);

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.status").asString().isEqualTo("REJECTED");
        assertThat(result).bodyJson().extractingPath("$.steps[1].decision").asString().isEqualTo("REJECTED");

        assertThat(status(6202)).isEqualTo("REJECTED");
        assertThat(steps(6202)).containsExactly("6311:APPROVED", "6312:REJECTED", "6313:SKIPPED", "6314:PENDING");
        assertThat(notices()).containsExactly("6101:REJECTED");
    }

    @Test
    @UseCase(id = "UC-026", scenario = "A5: コメントがない", businessRules = {"BR-005"})
    void needs_a_comment_to_send_back_from_the_main_track() {
        assertProblem(decide(6202, MAIN_1, "SEND_BACK", null, 1), 400, "/problems/invalid-input");
        assertThat(steps(6202)).contains("6312:PENDING");
    }

    @Test
    @UseCase(id = "UC-026", scenario = "A6: 判断の間に稟議が変わった", businessRules = {"BR-006"},
            requirements = {"NFR-003"})
    void records_nothing_when_the_step_was_decided_after_it_was_opened() {
        decide(6202, MAIN_1, "APPROVE", null, 1); // 別の画面から先に承認した

        MvcTestResult result = decide(6202, MAIN_1, "REJECT", "否決", 1);

        assertProblem(result, 409, "/problems/concurrent-change");
        assertThat(steps(6202)).contains("6312:APPROVED");
    }

    @Test
    @UseCase(id = "UC-026", scenario = "A6: 判断の間に稟議が変わった", requirements = {"NFR-003"})
    void records_nothing_when_the_request_was_resubmitted_after_it_was_opened() {
        // 6210 は、回 1 のとき(version 1)に開かれた後、差し戻されて再申請され、回 2(version 2)になった
        MvcTestResult result = decide(6210, MAIN_2, "APPROVE", null, 1);

        assertProblem(result, 409, "/problems/concurrent-change");
        assertThat(status(6210)).isEqualTo("IN_APPROVAL");
        // 開き直せば、新しい回で承認できる
        assertThat(decide(6210, MAIN_2, "APPROVE", null, 2)).bodyJson().extractingPath("$.status").asString()
                .isEqualTo("APPROVED");
    }

    @Test
    @UseCase(id = "UC-026", scenario = "A2: 今は判断できない", businessRules = {"BR-006", "BR-007"})
    void tells_why_a_user_cannot_decide_on_the_main_track() {
        // 報告先と起案者は、メイントラックの判断する段にいない(代理の判断はしない、BR-007)
        assertReason(decide(6202, REPORTER, "APPROVE", null, 1), "NOT_ASSIGNED");
        assertReason(decide(6202, DRAFTER, "APPROVE", null, 1), "NOT_ASSIGNED");
        // 判断は一度(BR-006)
        decide(6202, MAIN_1, "APPROVE", null, 1);
        assertReason(decide(6202, MAIN_1, "REJECT", "やはり否決", 2), "ALREADY_DECIDED");
        // 承認中でない稟議
        assertReason(decide(6203, MAIN_1, "APPROVE", null, 2), "NOT_IN_PROGRESS");
        assertThat(steps(6202)).containsExactly("6311:APPROVED", "6312:APPROVED", "6313:PENDING", "6314:PENDING");
    }

    @Test
    @UseCase(id = "UC-026", scenario = "A6: 判断の間に稟議が変わった", requirements = {"NFR-003"})
    void records_nothing_when_the_request_left_approval_after_it_was_opened() {
        int viewed = 1; // 承認 1段目が二つの画面で開き、一つめの画面で差し戻した
        decide(6202, MAIN_1, "SEND_BACK", "根拠を足してください", viewed);

        assertProblem(decide(6202, MAIN_1, "APPROVE", null, viewed), 409, "/problems/concurrent-change");
        assertThat(status(6202)).isEqualTo("DRAFT");
    }

    @Test
    @UseCase(id = "UC-026", scenario = "Main Success Scenario", businessRules = {"BR-001"})
    void lists_the_requests_the_approver_decides_now() {
        // 承認 1段目の番の稟議だけ。合議中の 6201 と、2段目の番の稟議は入らない
        assertThat(mvc.get().uri("/api/approval-requests/assigned").header("X-Acting-User", String.valueOf(MAIN_1))
                .exchange()).bodyJson().extractingPath("$[*].requestId").asArray().containsExactly(6202, 6205, 6206);
        assertThat(mvc.get().uri("/api/approval-requests/assigned").header("X-Acting-User", String.valueOf(MAIN_2))
                .exchange()).bodyJson().extractingPath("$[*].requestId").asArray().containsExactly(6210);
    }

    @Test
    @UseCase(id = "UC-026", scenario = "Preconditions")
    void lets_only_business_users_open_list_and_decide() {
        assertProblem(open(6202, ORG_ADMIN), 403, "/problems/forbidden");
        assertProblem(mvc.get().uri("/api/approval-requests/assigned")
                .header("X-Acting-User", String.valueOf(ORG_ADMIN)).exchange(), 403, "/problems/forbidden");
        assertProblem(decide(6202, ORG_ADMIN, "APPROVE", null, 1), 403, "/problems/forbidden");
        assertThat(status(6202)).isEqualTo("IN_APPROVAL");
    }

    @Test
    @UseCase(id = "UC-026", scenario = "A1: 稟議が見つからない", requirements = {"NFR-007"})
    void hides_requests_of_other_organizations() {
        assertProblem(open(6204, MAIN_1), 404, "/problems/not-found");
        assertProblem(decide(6204, MAIN_1, "APPROVE", null, 0), 404, "/problems/not-found");
        assertProblem(decide(999_999, MAIN_1, "APPROVE", null, 0), 404, "/problems/not-found");
    }

    // ---------------------------------------------------------------- 組成の稟議(UC-015 BR-006・BR-010)と A8

    @Test
    @UseCase(id = "UC-026", scenario = "Main Success Scenario", businessRules = {"BR-008"})
    void fixes_the_facility_and_forms_the_deal_when_the_formation_request_is_approved() {
        MvcTestResult result = decide(6205, MAIN_1, "APPROVE", null, 0);

        assertThat(result).hasStatusOk();
        // UC-015 BR-006: ファシリティは FIXED、UC-015 BR-010: 融資案件は組成済み
        assertThat(jdbc.queryForObject("select status from facility.facility where id = 6601", String.class))
                .isEqualTo("FIXED");
        assertThat(jdbc.queryForObject("select status from syndicate.deal where id = 6501", String.class))
                .isEqualTo("FORMED");
        // UC-015 BR-006: 出資者に、組成で決まった持分をお知らせで届ける
        assertThat(jdbc.queryForObject("select message from notification.notice where recipient_id = ?",
                String.class, INVESTOR)).contains("200,000,000 円");
    }

    @Test
    @UseCase(id = "UC-026", scenario = "A3: 承認者が差し戻す", businessRules = {"BR-008"})
    void returns_the_deal_to_preparing_when_the_formation_request_is_sent_back() {
        decide(6205, MAIN_1, "SEND_BACK", "持分を見直してください", 0);

        // UC-015 BR-006 / BR-010: ファシリティは組成中のまま直せる、融資案件は組成準備中に戻る
        assertThat(jdbc.queryForMap("select status, approval_request_id from facility.facility where id = 6601"))
                .containsEntry("status", "DRAFT").containsEntry("approval_request_id", null);
        assertThat(jdbc.queryForObject("select status from syndicate.deal where id = 6501", String.class))
                .isEqualTo("PREPARING");
    }

    @Test
    @UseCase(id = "UC-026", scenario = "A4: 承認者が否決する", businessRules = {"BR-008"})
    void returns_the_deal_to_preparing_when_the_formation_request_is_rejected() {
        decide(6205, MAIN_1, "REJECT", "見送ります", 0);

        assertThat(jdbc.queryForObject("select approval_request_id from facility.facility where id = 6601",
                Long.class)).isNull();
        assertThat(jdbc.queryForObject("select status from syndicate.deal where id = 6501", String.class))
                .isEqualTo("PREPARING");
    }

    @Test
    @UseCase(id = "UC-026", scenario = "A3: 承認者が差し戻す", businessRules = {"BR-008"})
    void sends_back_even_when_the_business_has_nothing_left_to_undo() {
        // A8 は最終決裁の承認だけ。差し戻しは、業務が戻すものがなくても記録する
        MvcTestResult result = decide(6206, MAIN_1, "SEND_BACK", "見直してください", 0);

        assertThat(result).hasStatusOk();
        assertThat(status(6206)).isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject("select status from syndicate.deal where id = 6502", String.class))
                .isEqualTo("FORMING");
    }

    /**
     * 判断と業務の処理が一緒に取り消されることは、テストのトランザクションの外でしか確かめられない
     * (クラスの @Transactional の中では、取り消しがテストの終わりまで起きない)。
     */
    @Test
    @UseCase(id = "UC-026", scenario = "A8: 業務が承認の結果を処理できない")
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void records_no_approval_when_the_business_cannot_take_it() {
        try {
            MvcTestResult result = decide(6206, MAIN_1, "APPROVE", null, 0);

            assertProblem(result, 409, "/problems/business-rule");
            // A8: 業務が処理できない理由を示す(文言は仕様が定めていないので、あることだけを確かめる)
            assertThat(result).bodyJson().extractingPath("$.detail").asString().isNotBlank();
            assertThat(status(6206)).isEqualTo("IN_APPROVAL");
            assertThat(steps(6206)).containsExactly("6351:PENDING");
            assertThat(jdbc.queryForObject("select count(*) from notification.notice where subject_ref like ?",
                    Integer.class, "approval-request:6206")).isZero();
            assertThat(jdbc.queryForObject("select status from syndicate.deal where id = 6502", String.class))
                    .isEqualTo("FORMING");
        } finally {
            // 取り消されなかった場合でも、他のテストのためにテストデータを元に戻す
            jdbc.update("update workflow.approval_request set status = 'IN_APPROVAL', decided_at = null, version = 0"
                    + " where id = 6206");
            jdbc.update("update workflow.approval_step set decision = 'PENDING', comment = null, decided_at = null"
                    + " where id = 6351");
            jdbc.update("delete from notification.notice where subject_ref = 'approval-request:6206'");
        }
    }

    MvcTestResult open(long requestId, long userId) {
        return mvc.get().uri("/api/approval-requests/{id}", requestId)
                .header("X-Acting-User", String.valueOf(userId)).exchange();
    }

    MvcTestResult decide(long requestId, long userId, String decision, String comment, int viewedVersion) {
        return mvc.post().uri("/api/approval-requests/{id}/decisions", requestId)
                .header("X-Acting-User", String.valueOf(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"decision": "%s", "comment": %s, "viewedVersion": %d}
                        """.formatted(decision, comment == null ? "null" : "\"" + comment + "\"", viewedVersion))
                .exchange();
    }

    String status(long requestId) {
        return jdbc.queryForObject("select status from workflow.approval_request where id = ?", String.class,
                requestId);
    }

    /** 段の id と判断を「id:判断」で、段の順に。 */
    List<String> steps(long requestId) {
        return jdbc.query("select id, decision from workflow.approval_step where request_id = ? order by id",
                (rs, n) -> rs.getLong(1) + ":" + rs.getString(2), requestId);
    }

    /** お知らせを「受け取る利用者:種類」で、届いた順に。 */
    List<String> notices() {
        List<String> notices = new ArrayList<>();
        jdbc.query("select recipient_id, kind from notification.notice order by id",
                rs -> {
                    notices.add(rs.getLong(1) + ":" + rs.getString(2));
                });
        return notices;
    }

    void assertReason(MvcTestResult result, String reason) {
        assertProblem(result, 409, "/problems/decision-not-allowed");
        assertThat(result).bodyJson().extractingPath("$.reason").asString().isEqualTo(reason);
    }

    void assertProblem(MvcTestResult result, int status, String type) {
        assertThat(result).hasStatus(status);
        assertThat(result).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.type").asString().isEqualTo(type);
    }
}
