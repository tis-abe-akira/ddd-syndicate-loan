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
 * UC-025 稟議に合議する: API の層のテスト(結合層)。テストデータは R__uc025_uc026_decide_approval_request.sql。
 * 判断の日時は 2026-12-01 09:00(日本時間)。拒否の文言は仕様が定めていないので、理由の種類を検証する。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Import(UC025ConsultApprovalRequestApiTest.Config.class)
class UC025ConsultApprovalRequestApiTest {

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
    @UseCase(id = "UC-025", scenario = "Main Success Scenario")
    void shows_the_request_and_its_route_to_a_consultee() {
        MvcTestResult result = open(6201, CONSULT_A);

        assertThat(result).hasStatusOk();
        var json = assertThat(result).bodyJson();
        json.extractingPath("$.title").asString().isEqualTo("合議中の稟議");
        json.extractingPath("$.body").asString().isEqualTo("本文");
        json.extractingPath("$.status").asString().isEqualTo("IN_CONSULTATION");
        json.extractingPath("$.drafterName").asString().isEqualTo("起案者");
        json.extractingPath("$.attributes.amount").asNumber().satisfies(n -> assertThat(n.longValue()).isEqualTo(500000000L));
        json.extractingPath("$.conditions.currency").asString().isEqualTo("JPY");
        json.extractingPath("$.steps[*].kind").asArray()
                .containsExactly("CONSULTATION", "CONSULTATION", "MAIN", "MAIN", "REPORT");
        json.extractingPath("$.steps[*].decision").asArray()
                .containsExactly("PENDING", "PENDING", "PENDING", "PENDING", "PENDING");
        json.extractingPath("$.blockedReason").isNull();
    }

    @Test
    @UseCase(id = "UC-025", scenario = "Main Success Scenario")
    void shows_the_decisions_and_comments_already_recorded() {
        var json = assertThat(open(6202, MAIN_1)).bodyJson();

        json.extractingPath("$.steps[0].kind").asString().isEqualTo("CONSULTATION");
        json.extractingPath("$.steps[0].decision").asString().isEqualTo("APPROVED");
        json.extractingPath("$.steps[0].comment").asString().isEqualTo("異議なし");
        json.extractingPath("$.steps[0].decidedAt").asString().isEqualTo("2026-11-30T11:00:00");
    }

    @Test
    @UseCase(id = "UC-025", scenario = "Main Success Scenario", businessRules = {"BR-002", "BR-005", "BR-010"})
    void keeps_the_request_in_consultation_until_every_consultee_approves() {
        MvcTestResult result = decide(6201, CONSULT_A, "APPROVE", null, 0);

        assertThat(result).hasStatusOk();
        var json = assertThat(result).bodyJson();
        json.extractingPath("$.status").asString().isEqualTo("IN_CONSULTATION");
        // step 8: 記録した判断を示す
        json.extractingPath("$.steps[0].decision").asString().isEqualTo("APPROVED");
        json.extractingPath("$.steps[0].decidedAt").asString().isEqualTo("2026-12-01T09:00:00");
        assertThat(steps(6201)).containsExactly("6301:APPROVED", "6302:PENDING", "6303:PENDING", "6304:PENDING",
                "6305:PENDING");
        // BR-010: 判断した日時を記録する。承認のコメントは任意(BR-005)
        assertThat(jdbc.queryForObject("select decided_at from workflow.approval_step where id = 6301",
                java.sql.Timestamp.class)).isEqualTo(java.sql.Timestamp.valueOf("2026-12-01 09:00:00"));
        assertThat(notices()).isEmpty();
    }

    @Test
    @UseCase(id = "UC-025", scenario = "Main Success Scenario", businessRules = {"BR-001", "BR-002", "BR-009"})
    void moves_the_request_to_the_main_track_when_every_consultee_approved_in_any_order() {
        decide(6201, CONSULT_B, "APPROVE", "異議なし", 0);

        MvcTestResult result = decide(6201, CONSULT_A, "APPROVE", null, 1);

        assertThat(result).bodyJson().extractingPath("$.status").asString().isEqualTo("IN_APPROVAL");
        // 承認のコメントも記録する(Post-S-1)
        assertThat(jdbc.queryForObject("select comment from workflow.approval_step where id = 6302", String.class))
                .isEqualTo("異議なし");
        assertThat(status(6201)).isEqualTo("IN_APPROVAL");
        // メイントラックの最初の承認者にだけ依頼が届く
        assertThat(notices()).containsExactly("6104:APPROVAL_REQUESTED");
    }

    @Test
    @UseCase(id = "UC-025", scenario = "Main Success Scenario")
    void lists_the_requests_the_consultee_decides_now() {
        assertThat(mvc.get().uri("/api/approval-requests/assigned").header("X-Acting-User", String.valueOf(CONSULT_A))
                .exchange()).bodyJson().extractingPath("$[*].requestId").asArray().containsExactly(6201, 6207, 6208);
        // 承認 1段目の利用者には、合議中の 6201 はまだ届いていない(6202 だけ)
        assertThat(mvc.get().uri("/api/approval-requests/assigned").header("X-Acting-User", String.valueOf(MAIN_1))
                .exchange()).bodyJson().extractingPath("$[*].requestId").asArray().containsExactly(6202, 6205, 6206);
    }

    // ---------------------------------------------------------------- A3、A4、A5

    @Test
    @UseCase(id = "UC-025", scenario = "A3: 合議先が差し戻す", businessRules = {"BR-003", "BR-008", "BR-009"})
    void sends_the_request_back_to_the_drafter_and_skips_the_undecided_steps() {
        MvcTestResult result = decide(6201, CONSULT_A, "SEND_BACK", "金額の根拠を書き足してください", 0);

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.status").asString().isEqualTo("DRAFT");
        assertThat(result).bodyJson().extractingPath("$.steps[0].comment").asString()
                .isEqualTo("金額の根拠を書き足してください");
        assertThat(status(6201)).isEqualTo("DRAFT");
        // 報告先の段は判断する段ではないので、飛ばさずに残る
        assertThat(steps(6201)).containsExactly("6301:SENT_BACK", "6302:SKIPPED", "6303:SKIPPED", "6304:SKIPPED",
                "6305:PENDING");
        assertThat(jdbc.queryForObject("select comment from workflow.approval_step where id = 6301", String.class))
                .isEqualTo("金額の根拠を書き足してください");
        assertThat(notices()).containsExactly("6101:SENT_BACK");
    }

    @Test
    @UseCase(id = "UC-025", scenario = "A4: 合議先が否決する", businessRules = {"BR-004", "BR-008", "BR-009"})
    void rejects_the_request_and_records_when() {
        MvcTestResult result = decide(6201, CONSULT_B, "REJECT", "条件が合わない", 0);

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.status").asString().isEqualTo("REJECTED");
        assertThat(result).bodyJson().extractingPath("$.steps[1].decision").asString().isEqualTo("REJECTED");
        assertThat(status(6201)).isEqualTo("REJECTED");
        assertThat(jdbc.queryForObject("select decided_at from workflow.approval_request where id = 6201",
                java.sql.Timestamp.class)).isEqualTo(java.sql.Timestamp.valueOf("2026-12-01 09:00:00"));
        assertThat(steps(6201)).containsExactly("6301:SKIPPED", "6302:REJECTED", "6303:SKIPPED", "6304:SKIPPED",
                "6305:PENDING");
        assertThat(notices()).containsExactly("6101:REJECTED");
    }

    @Test
    @UseCase(id = "UC-025", scenario = "A5: コメントがない", businessRules = {"BR-005"})
    void needs_a_comment_to_send_back_or_reject() {
        for (String decision : List.of("SEND_BACK", "REJECT")) {
            MvcTestResult result = decide(6201, CONSULT_A, decision, " ", 0);

            assertProblem(result, 400, "/problems/invalid-input");
            assertThat(result).bodyJson().extractingPath("$.errors[*].field").asArray().containsExactly("comment");
        }
        assertThat(status(6201)).isEqualTo("IN_CONSULTATION");
        assertThat(steps(6201)).allMatch(step -> step.endsWith(":PENDING"));
    }

    @Test
    @UseCase(id = "UC-025", scenario = "A3: 合議先が差し戻す", businessRules = {"BR-008"})
    void tells_the_business_when_a_consultee_sends_the_formation_request_back() {
        assertThat(decide(6207, CONSULT_A, "SEND_BACK", "持分を見直してください", 0)).hasStatusOk();

        // 業務(UC-015 BR-010)が差し戻しを受けて、ファシリティを直せるようにし、融資案件を組成準備中に戻す
        assertThat(jdbc.queryForObject("select approval_request_id from facility.facility where id = 6603",
                Long.class)).isNull();
        assertThat(jdbc.queryForObject("select status from syndicate.deal where id = 6503", String.class))
                .isEqualTo("PREPARING");
    }

    @Test
    @UseCase(id = "UC-025", scenario = "A4: 合議先が否決する", businessRules = {"BR-008"})
    void tells_the_business_when_a_consultee_rejects_the_formation_request() {
        assertThat(decide(6208, CONSULT_A, "REJECT", "見送ります", 0)).hasStatusOk();

        assertThat(jdbc.queryForObject("select approval_request_id from facility.facility where id = 6604",
                Long.class)).isNull();
        assertThat(jdbc.queryForObject("select status from syndicate.deal where id = 6504", String.class))
                .isEqualTo("PREPARING");
    }

    // ---------------------------------------------------------------- A1、A2、A6、前提条件

    @Test
    @UseCase(id = "UC-025", scenario = "A1: 稟議が見つからない", requirements = {"NFR-007"})
    void hides_requests_of_other_organizations() {
        assertProblem(open(6201, OTHER_BANK), 404, "/problems/not-found");
        assertProblem(decide(6201, OTHER_BANK, "APPROVE", null, 0), 404, "/problems/not-found");
        assertProblem(open(999_999, CONSULT_A), 404, "/problems/not-found");
        assertThat(status(6201)).isEqualTo("IN_CONSULTATION");
    }

    @Test
    @UseCase(id = "UC-025", scenario = "A2: 今は判断できない", businessRules = {"BR-006", "BR-007"})
    void tells_why_a_user_cannot_decide_now() {
        // 合議中はメイントラックの承認者の番ではない
        assertThat(open(6201, MAIN_1)).bodyJson().extractingPath("$.blockedReason").asString().isEqualTo("NOT_YOUR_TURN");
        assertReason(decide(6201, MAIN_1, "APPROVE", null, 0), "NOT_YOUR_TURN");
        // 報告先や起案者は判断する段にいない(代理の判断はしない、BR-007)
        assertReason(decide(6201, REPORTER, "APPROVE", null, 0), "NOT_ASSIGNED");
        assertReason(decide(6201, DRAFTER, "APPROVE", null, 0), "NOT_ASSIGNED");
        // 判断は一度(BR-006)
        decide(6201, CONSULT_A, "APPROVE", null, 0);
        assertReason(decide(6201, CONSULT_A, "REJECT", "やはり否決", 1), "ALREADY_DECIDED");
        // 合議中でも承認中でもない稟議
        assertReason(decide(6203, MAIN_1, "APPROVE", null, 2), "NOT_IN_PROGRESS");
        assertThat(steps(6201)).startsWith("6301:APPROVED");
    }

    @Test
    @UseCase(id = "UC-025", scenario = "A6: 判断の間に稟議が変わった", requirements = {"NFR-003"})
    void records_nothing_when_another_consultee_sent_the_request_back_after_it_was_opened() {
        int viewed = 0; // 合議 A が開いたときのバージョン
        decide(6201, CONSULT_B, "SEND_BACK", "差し戻します", 0);

        MvcTestResult result = decide(6201, CONSULT_A, "APPROVE", null, viewed);

        assertProblem(result, 409, "/problems/concurrent-change");
        assertThat(steps(6201)).startsWith("6301:SKIPPED");
    }

    @Test
    @UseCase(id = "UC-025", scenario = "A6: 判断の間に稟議が変わった", requirements = {"NFR-003"})
    void records_nothing_when_the_request_was_resubmitted_after_it_was_opened() {
        // 6209 は、回 1 のとき(version 2)に開かれた後、差し戻されて再申請され、回 2(version 3)になった
        MvcTestResult result = decide(6209, CONSULT_B, "APPROVE", null, 2);

        assertProblem(result, 409, "/problems/concurrent-change");
        assertThat(steps(6209)).contains("6384:PENDING");
        // 開き直せば、新しい回で判断できる
        assertThat(open(6209, CONSULT_B)).bodyJson().extractingPath("$.version").asNumber()
                .satisfies(n -> assertThat(n.intValue()).isEqualTo(3));
        assertThat(decide(6209, CONSULT_B, "APPROVE", null, 3)).hasStatusOk();
        assertThat(steps(6209)).contains("6384:APPROVED");
    }

    @Test
    @UseCase(id = "UC-025", scenario = "Main Success Scenario", businessRules = {"BR-001"})
    void lets_a_consultee_approve_after_another_one_approved_in_parallel() {
        int viewed = 0; // 合議 A が開いた後、合議 B が先に承認する
        decide(6201, CONSULT_B, "APPROVE", null, 0);

        MvcTestResult result = decide(6201, CONSULT_A, "APPROVE", null, viewed);

        assertThat(result).hasStatusOk();
        assertThat(status(6201)).isEqualTo("IN_APPROVAL");
    }

    @Test
    @UseCase(id = "UC-025", scenario = "Main Success Scenario")
    void lets_only_business_users_open_list_and_decide() {
        assertProblem(open(6201, ORG_ADMIN), 403, "/problems/forbidden");
        assertProblem(mvc.get().uri("/api/approval-requests/assigned")
                .header("X-Acting-User", String.valueOf(ORG_ADMIN)).exchange(), 403, "/problems/forbidden");
        assertProblem(decide(6201, ORG_ADMIN, "APPROVE", null, 0), 403, "/problems/forbidden");
        assertThat(status(6201)).isEqualTo("IN_CONSULTATION");
    }

    void assertReason(MvcTestResult result, String reason) {
        assertProblem(result, 409, "/problems/decision-not-allowed");
        assertThat(result).bodyJson().extractingPath("$.reason").asString().isEqualTo(reason);
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

    void assertProblem(MvcTestResult result, int status, String type) {
        assertThat(result).hasStatus(status);
        assertThat(result).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.type").asString().isEqualTo(type);
    }
}
