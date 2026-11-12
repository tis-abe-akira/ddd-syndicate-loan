package com.example.syndicateloan.facility.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.syndicateloan.usecase.UseCase;
import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
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
 * UC-015 ファシリティを組成する: API の層のテスト(この use case の結合層)。
 *
 * <ul>
 *   <li>コントローラ → サービス → jOOQ → Testcontainers の PostgreSQL。何もモックしない。</li>
 *   <li>テストデータは {@code src/test/resources/db/migration/R__uc015_form_facility.sql}。</li>
 *   <li>各テストはトランザクションの中で動き、終わるとロールバックする。</li>
 *   <li>業務の「今日」は 2026-12-01(日本時間)に固定する。</li>
 *   <li>拒否の文言は仕様が定めていないので検証しない。Problem Details の type と、直すべき項目(field)を検証する。</li>
 * </ul>
 *
 * <p>稟議の起案(step 7、8、A4、A8、A10、BR-005、BR-006 の確定、BR-010)は未実装(Issue #31)のため、ここには無い。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Import(UC015FormFacilityApiTest.Config.class)
class UC015FormFacilityApiTest {

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

    // テストデータ(R__uc015_form_facility.sql)
    private static final long LEAD = 9001;
    private static final long INVESTOR_A = 9002; // 応諾額 100,000,000
    private static final long INVESTOR_B = 9003; // 応諾額 50,000,000
    private static final long INVESTOR_C = 9004; // 招待中(応諾していない)
    private static final long LEAD_USER = 9101;
    private static final long LEAD_ADMIN = 9102; // 業務ユーザーではない
    private static final long LEAD_RETIRED = 9103; // 無効
    private static final long OTHER_BANK_USER = 9104;
    private static final long DEAL_NEW = 9201;
    private static final long DEAL_NO_INVESTOR = 9202;
    private static final long DEAL_DRAFT = 9203; // ファシリティ 9301、version 3
    private static final long DEAL_FIXED = 9204;
    private static final long DEAL_UNDER_APPROVAL = 9205;
    private static final long DEAL_WITHDRAWN = 9206;
    private static final long DEAL_SCREENING = 9207;
    private static final String TODAY = "2026-12-01";
    private static final String YESTERDAY = "2026-11-30";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JdbcTemplate jdbc;

    // ---------------------------------------------------------------- Main success scenario

    @Test
    @UseCase(id = "UC-015")
    void shows_the_accepted_investors_and_their_total_when_formation_starts() {
        MvcTestResult result = start(DEAL_NEW, LEAD_USER);

        assertThat(result).hasStatusOk();
        var json = assertThat(result).bodyJson();
        json.extractingPath("$.dealId").asNumber().isEqualTo(9201);
        json.extractingPath("$.currency").asString().isEqualTo("JPY");
        json.extractingPath("$.blockedReason").isNull();
        // BR-001: 招待中の出資者 C は示さない
        json.extractingPath("$.investors[*].organizationId").asArray()
                .containsExactly(9002, 9003);
        json.extractingPath("$.investors[0].commitmentAmount").asNumber().satisfies(n ->
                assertThat(n.doubleValue()).isEqualTo(100_000_000d));
        json.extractingPath("$.investors[1].commitmentAmount").asNumber().satisfies(n ->
                assertThat(n.doubleValue()).isEqualTo(50_000_000d));
        json.extractingPath("$.totalAcceptedAmount").asNumber().satisfies(n ->
                assertThat(n.doubleValue()).isEqualTo(150_000_000d));
        json.extractingPath("$.facility").isNull();
        json.extractingPath("$.participationFingerprint").asString().isNotBlank();
    }

    @Test
    @UseCase(id = "UC-015", businessRules = {"BR-001", "BR-002", "BR-008", "BR-009"})
    void registers_the_facility_under_formation_with_the_decided_share_pie() {
        String fingerprint = fingerprint(DEAL_NEW);

        MvcTestResult result = form(DEAL_NEW, LEAD_USER, fingerprint, null,
                Map.of(INVESTOR_A, "80000000", INVESTOR_B, "50000000"), "20000000", "2027-01-04", "2027-12-30");

        assertThat(result).hasStatusOk();
        var json = assertThat(result).bodyJson();
        json.extractingPath("$.dealId").asNumber().isEqualTo(9201);
        json.extractingPath("$.status").asString().isEqualTo("DRAFT");
        // BR-008: 融資案件の通貨
        json.extractingPath("$.currency").asString().isEqualTo("JPY");
        json.extractingPath("$.startDate").asString().isEqualTo("2027-01-04");
        json.extractingPath("$.endDate").asString().isEqualTo("2027-12-30");
        // BR-002: コミットメント額はシェアパイの合計
        json.extractingPath("$.commitmentAmount").asNumber().satisfies(n ->
                assertThat(n.doubleValue()).isEqualTo(150_000_000d));
        assertThat(shares(DEAL_NEW)).containsExactlyInAnyOrderEntriesOf(Map.of(
                INVESTOR_A, 80_000_000d, INVESTOR_B, 50_000_000d, LEAD, 20_000_000d));

        // 事後条件: 組成を始め直すと、組成中のファシリティと決めた持分が示される(A3)
        var view = assertThat(start(DEAL_NEW, LEAD_USER)).bodyJson();
        view.extractingPath("$.facility.status").asString().isEqualTo("DRAFT");
        view.extractingPath("$.facility.commitmentAmount").asNumber().satisfies(n ->
                assertThat(n.doubleValue()).isEqualTo(150_000_000d));
        view.extractingPath("$.investors[0].shareAmount").asNumber().satisfies(n ->
                assertThat(n.doubleValue()).isEqualTo(80_000_000d));
        view.extractingPath("$.leadCommitmentAmount").asNumber().satisfies(n ->
                assertThat(n.doubleValue()).isEqualTo(20_000_000d));
    }

    // ---------------------------------------------------------------- BR-001、BR-009: 持分

    @Test
    @UseCase(id = "UC-015", businessRules = {"BR-009"})
    void accepts_a_share_equal_to_the_accepted_commitment() {
        MvcTestResult result = form(DEAL_NEW, LEAD_USER, fingerprint(DEAL_NEW), null,
                Map.of(INVESTOR_A, "100000000", INVESTOR_B, "50000000"), "0", "2027-01-04", "2027-12-30");

        assertThat(result).hasStatusOk();
        assertThat(shares(DEAL_NEW)).containsEntry(INVESTOR_A, 100_000_000d);
    }

    @Test
    @UseCase(id = "UC-015", scenario = "A5: 持分、引き出せる期間、リード銀行の額が正しくない",
            businessRules = {"BR-009"})
    void rejects_a_share_above_the_accepted_commitment() {
        MvcTestResult result = form(DEAL_NEW, LEAD_USER, fingerprint(DEAL_NEW), null,
                Map.of(INVESTOR_A, "100000000.01", INVESTOR_B, "50000000"), "0", "2027-01-04", "2027-12-30");

        assertInvalid(result, "investorShares[9002]");
        assertNoFacility(DEAL_NEW);
    }

    @Test
    @UseCase(id = "UC-015", businessRules = {"BR-001", "BR-009"})
    void leaves_an_investor_with_a_zero_share_out_of_the_pie() {
        MvcTestResult result = form(DEAL_NEW, LEAD_USER, fingerprint(DEAL_NEW), null,
                Map.of(INVESTOR_A, "100000000", INVESTOR_B, "0"), "0", "2027-01-04", "2027-12-30");

        assertThat(result).hasStatusOk();
        assertThat(shares(DEAL_NEW)).containsOnlyKeys(INVESTOR_A);
    }

    @Test
    @UseCase(id = "UC-015", scenario = "A5: 持分、引き出せる期間、リード銀行の額が正しくない",
            businessRules = {"BR-009"})
    void rejects_a_share_below_zero() {
        MvcTestResult result = form(DEAL_NEW, LEAD_USER, fingerprint(DEAL_NEW), null,
                Map.of(INVESTOR_A, "100000000", INVESTOR_B, "-0.01"), "0", "2027-01-04", "2027-12-30");

        assertInvalid(result, "investorShares[9003]");
        assertNoFacility(DEAL_NEW);
    }

    @Test
    @UseCase(id = "UC-015", scenario = "A5: 持分、引き出せる期間、リード銀行の額が正しくない",
            businessRules = {"BR-001"})
    void rejects_a_share_for_an_investor_who_has_not_accepted() {
        MvcTestResult result = form(DEAL_NEW, LEAD_USER, fingerprint(DEAL_NEW), null,
                Map.of(INVESTOR_A, "100000000", INVESTOR_B, "50000000", INVESTOR_C, "10000000"), "0",
                "2027-01-04", "2027-12-30");

        assertInvalid(result, "investorShares[9004]");
        assertNoFacility(DEAL_NEW);
    }

    @Test
    @UseCase(id = "UC-015", scenario = "A5: 持分、引き出せる期間、リード銀行の額が正しくない",
            businessRules = {"BR-001"})
    void rejects_a_pie_without_any_share_above_zero() {
        MvcTestResult result = form(DEAL_NEW, LEAD_USER, fingerprint(DEAL_NEW), null,
                Map.of(INVESTOR_A, "0", INVESTOR_B, "0"), "0", "2027-01-04", "2027-12-30");

        assertInvalid(result, "investorShares");
        assertNoFacility(DEAL_NEW);
    }

    // ---------------------------------------------------------------- BR-001: リード銀行の額

    @Test
    @UseCase(id = "UC-015", businessRules = {"BR-001"})
    void puts_the_lead_bank_into_the_pie_with_a_commitment_above_zero() {
        MvcTestResult result = form(DEAL_NEW, LEAD_USER, fingerprint(DEAL_NEW), null,
                Map.of(INVESTOR_A, "0", INVESTOR_B, "0"), "0.01", "2027-01-04", "2027-12-30");

        assertThat(result).hasStatusOk();
        assertThat(shares(DEAL_NEW)).containsExactlyEntriesOf(Map.of(LEAD, 0.01d));
    }

    @Test
    @UseCase(id = "UC-015", scenario = "A5: 持分、引き出せる期間、リード銀行の額が正しくない",
            businessRules = {"BR-001"})
    void rejects_a_lead_commitment_below_zero() {
        MvcTestResult result = form(DEAL_NEW, LEAD_USER, fingerprint(DEAL_NEW), null,
                Map.of(INVESTOR_A, "100000000", INVESTOR_B, "50000000"), "-0.01", "2027-01-04", "2027-12-30");

        assertInvalid(result, "leadCommitmentAmount");
        assertNoFacility(DEAL_NEW);
    }

    // ---------------------------------------------------------------- BR-003: 引き出せる期間

    @Test
    @UseCase(id = "UC-015", businessRules = {"BR-003"})
    void accepts_a_period_from_today_that_ends_the_next_day() {
        MvcTestResult result = form(DEAL_NEW, LEAD_USER, fingerprint(DEAL_NEW), null,
                Map.of(INVESTOR_A, "100000000", INVESTOR_B, "50000000"), "0", TODAY, "2026-12-02");

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.startDate").asString().isEqualTo(TODAY);
    }

    @Test
    @UseCase(id = "UC-015", scenario = "A5: 持分、引き出せる期間、リード銀行の額が正しくない",
            businessRules = {"BR-003"})
    void rejects_a_period_that_starts_yesterday() {
        MvcTestResult result = form(DEAL_NEW, LEAD_USER, fingerprint(DEAL_NEW), null,
                Map.of(INVESTOR_A, "100000000", INVESTOR_B, "50000000"), "0", YESTERDAY, "2027-12-30");

        assertInvalid(result, "startDate");
        assertNoFacility(DEAL_NEW);
    }

    @Test
    @UseCase(id = "UC-015", scenario = "A5: 持分、引き出せる期間、リード銀行の額が正しくない",
            businessRules = {"BR-003"})
    void rejects_a_period_that_ends_on_its_first_day() {
        MvcTestResult result = form(DEAL_NEW, LEAD_USER, fingerprint(DEAL_NEW), null,
                Map.of(INVESTOR_A, "100000000", INVESTOR_B, "50000000"), "0", "2027-01-04", "2027-01-04");

        assertInvalid(result, "endDate");
        assertNoFacility(DEAL_NEW);
    }

    @Test
    @UseCase(id = "UC-015", scenario = "A5: 持分、引き出せる期間、リード銀行の額が正しくない",
            businessRules = {"BR-003"})
    void rejects_an_empty_period_and_shows_every_field_to_correct() {
        MvcTestResult result = form(DEAL_NEW, LEAD_USER, fingerprint(DEAL_NEW), null,
                Map.of(INVESTOR_A, "100000001", INVESTOR_B, "50000000"), "0", null, null);

        assertInvalid(result, "investorShares[9002]", "startDate", "endDate");
        assertNoFacility(DEAL_NEW);
    }

    // ---------------------------------------------------------------- A1、A2、A3

    @Test
    @UseCase(id = "UC-015", scenario = "A1: 参加を応諾した出資者がいない")
    void shows_that_no_investor_has_accepted_and_refuses_to_form() {
        assertThat(start(DEAL_NO_INVESTOR, LEAD_USER)).bodyJson()
                .extractingPath("$.blockedReason").asString().isEqualTo("NO_ACCEPTED_INVESTOR");

        MvcTestResult result = form(DEAL_NO_INVESTOR, LEAD_USER, fingerprint(DEAL_NO_INVESTOR), null,
                Map.of(), "10000000", "2027-01-04", "2027-12-30");

        assertProblem(result, 409, "/problems/business-rule");
        assertNoFacility(DEAL_NO_INVESTOR);
    }

    @Test
    @UseCase(id = "UC-015", scenario = "A2: 確定したか稟議中のファシリティがある", businessRules = {"BR-006"})
    void shows_a_fixed_facility_and_does_not_change_it() {
        var view = assertThat(start(DEAL_FIXED, LEAD_USER)).bodyJson();
        view.extractingPath("$.blockedReason").asString().isEqualTo("FACILITY_LOCKED");
        view.extractingPath("$.facility.status").asString().isEqualTo("FIXED");

        MvcTestResult result = form(DEAL_FIXED, LEAD_USER, fingerprint(DEAL_FIXED), 5,
                Map.of(INVESTOR_A, "90000000"), "0", "2027-01-04", "2027-12-30");

        assertProblem(result, 409, "/problems/business-rule");
        assertThat(shares(DEAL_FIXED)).containsExactlyEntriesOf(Map.of(INVESTOR_A, 100_000_000d));
    }

    @Test
    @UseCase(id = "UC-015", scenario = "A2: 確定したか稟議中のファシリティがある", businessRules = {"BR-007"})
    void shows_a_facility_under_approval_and_does_not_change_it() {
        var view = assertThat(start(DEAL_UNDER_APPROVAL, LEAD_USER)).bodyJson();
        view.extractingPath("$.blockedReason").asString().isEqualTo("FACILITY_LOCKED");
        view.extractingPath("$.facility.underApproval").asBoolean().isTrue();

        MvcTestResult result = form(DEAL_UNDER_APPROVAL, LEAD_USER, fingerprint(DEAL_UNDER_APPROVAL), 2,
                Map.of(INVESTOR_A, "90000000"), "0", "2027-01-04", "2027-12-30");

        assertProblem(result, 409, "/problems/business-rule");
        assertThat(shares(DEAL_UNDER_APPROVAL)).containsExactlyEntriesOf(Map.of(INVESTOR_A, 100_000_000d));
        assertThat(facilityPeriod(DEAL_UNDER_APPROVAL)).containsExactly("2027-01-04", "2027-12-30");
    }

    @Test
    @UseCase(id = "UC-015", scenario = "A3: 組成中のファシリティを直す", businessRules = {"BR-004"})
    void shows_the_current_shares_of_the_facility_under_formation_and_revises_it() {
        var view = assertThat(start(DEAL_DRAFT, LEAD_USER)).bodyJson();
        view.extractingPath("$.blockedReason").isNull();
        view.extractingPath("$.facility.status").asString().isEqualTo("DRAFT");
        view.extractingPath("$.facility.startDate").asString().isEqualTo("2027-01-04");
        view.extractingPath("$.facility.endDate").asString().isEqualTo("2027-12-30");
        view.extractingPath("$.facility.version").asNumber().isEqualTo(3);
        view.extractingPath("$.investors[0].shareAmount").asNumber().satisfies(n ->
                assertThat(n.doubleValue()).isEqualTo(80_000_000d));
        view.extractingPath("$.investors[1].shareAmount").asNumber().satisfies(n ->
                assertThat(n.doubleValue()).isEqualTo(0d));
        view.extractingPath("$.leadCommitmentAmount").asNumber().satisfies(n ->
                assertThat(n.doubleValue()).isEqualTo(20_000_000d));

        MvcTestResult result = form(DEAL_DRAFT, LEAD_USER, fingerprint(DEAL_DRAFT), 3,
                Map.of(INVESTOR_A, "100000000", INVESTOR_B, "50000000"), "0", "2027-02-01", "2028-01-31");

        assertThat(result).hasStatusOk();
        // BR-004: 同じファシリティを直す(二つ目を作らない)
        assertThat(result).bodyJson().extractingPath("$.id").asNumber().isEqualTo(9301);
        assertThat(result).bodyJson().extractingPath("$.version").asNumber().isEqualTo(4);
        assertThat(jdbc.queryForObject("select count(*) from facility.facility where deal_id = ?",
                Integer.class, DEAL_DRAFT)).isEqualTo(1);
        assertThat(shares(DEAL_DRAFT)).containsExactlyInAnyOrderEntriesOf(Map.of(
                INVESTOR_A, 100_000_000d, INVESTOR_B, 50_000_000d));
        assertThat(facilityPeriod(DEAL_DRAFT)).containsExactly("2027-02-01", "2028-01-31");
    }

    // ---------------------------------------------------------------- A6

    @Test
    @UseCase(id = "UC-015", scenario = "A6: 組成の間に参加の回答かファシリティが変わった",
            requirements = {"NFR-003"})
    void registers_nothing_when_an_accepted_commitment_changed_after_formation_started() {
        String fingerprint = fingerprint(DEAL_NEW);
        // 組成を始めた後に、別の操作で出資者 B の応諾額が変わる
        jdbc.update("update syndicate.deal_participant set commitment_amount = 60000000 where id = 9402");

        MvcTestResult result = form(DEAL_NEW, LEAD_USER, fingerprint, null,
                Map.of(INVESTOR_A, "100000000", INVESTOR_B, "50000000"), "0", "2027-01-04", "2027-12-30");

        assertProblem(result, 409, "/problems/concurrent-change");
        assertNoFacility(DEAL_NEW);
    }

    @Test
    @UseCase(id = "UC-015", scenario = "A6: 組成の間に参加の回答かファシリティが変わった",
            requirements = {"NFR-003"})
    void registers_nothing_when_an_investor_accepted_after_formation_started() {
        String fingerprint = fingerprint(DEAL_NEW);
        // 組成を始めた後に、別の操作で出資者 C が応諾する
        jdbc.update("update syndicate.deal_participant set status = 'ACCEPTED', commitment_amount = 10000000"
                + " where id = 9403");

        MvcTestResult result = form(DEAL_NEW, LEAD_USER, fingerprint, null,
                Map.of(INVESTOR_A, "100000000", INVESTOR_B, "50000000"), "0", "2027-01-04", "2027-12-30");

        assertProblem(result, 409, "/problems/concurrent-change");
        assertNoFacility(DEAL_NEW);
    }

    @Test
    @UseCase(id = "UC-015", scenario = "A6: 組成の間に参加の回答かファシリティが変わった",
            requirements = {"NFR-003"})
    void does_not_overwrite_a_facility_another_operation_revised() {
        String fingerprint = fingerprint(DEAL_DRAFT);
        // 組成を始めた後に、別の担当者がファシリティを直す
        jdbc.update("update facility.facility set version = 4 where id = 9301");

        MvcTestResult result = form(DEAL_DRAFT, LEAD_USER, fingerprint, 3,
                Map.of(INVESTOR_A, "100000000", INVESTOR_B, "50000000"), "0", "2027-01-04", "2027-12-30");

        assertProblem(result, 409, "/problems/concurrent-change");
        assertThat(shares(DEAL_DRAFT)).containsExactlyInAnyOrderEntriesOf(Map.of(
                INVESTOR_A, 80_000_000d, LEAD, 20_000_000d));
    }

    @Test
    @UseCase(id = "UC-015", scenario = "A6: 組成の間に参加の回答かファシリティが変わった",
            requirements = {"NFR-003"})
    void does_not_overwrite_a_facility_another_operation_registered() {
        String fingerprint = fingerprint(DEAL_NEW);
        // 組成を始めた後に、別の担当者がファシリティを登録する
        MvcTestResult first = form(DEAL_NEW, LEAD_USER, fingerprint, null,
                Map.of(INVESTOR_A, "90000000", INVESTOR_B, "50000000"), "0", "2027-01-04", "2027-12-30");
        assertThat(first).hasStatusOk();

        MvcTestResult second = form(DEAL_NEW, LEAD_USER, fingerprint, null,
                Map.of(INVESTOR_A, "70000000", INVESTOR_B, "50000000"), "0", "2027-01-04", "2027-12-30");

        assertProblem(second, 409, "/problems/concurrent-change");
        assertThat(shares(DEAL_NEW)).containsEntry(INVESTOR_A, 90_000_000d);
    }

    @Test
    @UseCase(id = "UC-015", scenario = "A6: 組成の間に参加の回答かファシリティが変わった",
            businessRules = {"BR-007"}, requirements = {"NFR-003"})
    void does_not_change_a_facility_put_under_approval_after_formation_started() {
        String fingerprint = fingerprint(DEAL_DRAFT);
        // 組成を始めた後に、別の担当者が組成の稟議にかける
        jdbc.update("update facility.facility set approval_request_id = 8003, version = 4 where id = 9301");

        MvcTestResult result = form(DEAL_DRAFT, LEAD_USER, fingerprint, 3,
                Map.of(INVESTOR_A, "100000000", INVESTOR_B, "50000000"), "0", "2027-01-04", "2027-12-30");

        assertThat(result).hasStatus(409);
        assertThat(result).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(shares(DEAL_DRAFT)).containsExactlyInAnyOrderEntriesOf(Map.of(
                INVESTOR_A, 80_000_000d, LEAD, 20_000_000d));
    }

    // ---------------------------------------------------------------- A9 と前提条件

    @Test
    @UseCase(id = "UC-015", scenario = "A9: 組成の間に融資案件が取り下げられた")
    void registers_nothing_when_the_deal_was_withdrawn_after_formation_started() {
        String fingerprint = fingerprint(DEAL_NEW);
        jdbc.update("update syndicate.deal set status = 'WITHDRAWN' where id = ?", DEAL_NEW);

        MvcTestResult result = form(DEAL_NEW, LEAD_USER, fingerprint, null,
                Map.of(INVESTOR_A, "100000000", INVESTOR_B, "50000000"), "0", "2027-01-04", "2027-12-30");

        assertProblem(result, 409, "/problems/business-rule");
        assertNoFacility(DEAL_NEW);
    }

    @Test
    @UseCase(id = "UC-015", scenario = "A9: 組成の間に融資案件が取り下げられた")
    void registers_nothing_when_the_deal_was_declined_after_formation_started() {
        String fingerprint = fingerprint(DEAL_NEW);
        jdbc.update("update syndicate.deal set status = 'DECLINED' where id = ?", DEAL_NEW);

        MvcTestResult result = form(DEAL_NEW, LEAD_USER, fingerprint, null,
                Map.of(INVESTOR_A, "100000000", INVESTOR_B, "50000000"), "0", "2027-01-04", "2027-12-30");

        assertProblem(result, 409, "/problems/business-rule");
        assertNoFacility(DEAL_NEW);
    }

    @Test
    @UseCase(id = "UC-015", scenario = "A9: 組成の間に融資案件が取り下げられた")
    void shows_that_a_withdrawn_deal_cannot_be_formed() {
        assertThat(start(DEAL_WITHDRAWN, LEAD_USER)).bodyJson()
                .extractingPath("$.blockedReason").asString().isEqualTo("DEAL_CLOSED");
    }

    @Test
    @UseCase(id = "UC-015")
    void refuses_to_form_a_deal_whose_screening_is_not_approved() {
        // 前提条件: 借り手審査の稟議が承認されている(融資案件が組成準備中)
        assertThat(start(DEAL_SCREENING, LEAD_USER)).bodyJson()
                .extractingPath("$.blockedReason").asString().isEqualTo("DEAL_NOT_PREPARING");

        MvcTestResult result = form(DEAL_SCREENING, LEAD_USER, fingerprint(DEAL_SCREENING), null,
                Map.of(INVESTOR_A, "100000000"), "0", "2027-01-04", "2027-12-30");

        assertProblem(result, 409, "/problems/business-rule");
        assertNoFacility(DEAL_SCREENING);
    }

    // ---------------------------------------------------------------- NFR-007: アクセス

    @Test
    @UseCase(id = "UC-015", requirements = {"NFR-007"})
    void hides_the_deal_from_a_user_of_another_bank() {
        assertProblem(start(DEAL_NEW, OTHER_BANK_USER), 404, "/problems/not-found");

        MvcTestResult result = form(DEAL_NEW, OTHER_BANK_USER, fingerprint(DEAL_NEW), null,
                Map.of(INVESTOR_A, "100000000", INVESTOR_B, "50000000"), "0", "2027-01-04", "2027-12-30");

        assertProblem(result, 404, "/problems/not-found");
        assertNoFacility(DEAL_NEW);
    }

    @Test
    @UseCase(id = "UC-015", requirements = {"NFR-007"})
    void returns_not_found_for_an_unknown_deal() {
        assertProblem(start(999_999, LEAD_USER), 404, "/problems/not-found");
    }

    @Test
    @UseCase(id = "UC-015", requirements = {"NFR-007"})
    void forbids_a_lead_bank_user_without_the_business_role() {
        assertProblem(start(DEAL_NEW, LEAD_ADMIN), 403, "/problems/forbidden");

        MvcTestResult result = form(DEAL_NEW, LEAD_ADMIN, fingerprint(DEAL_NEW), null,
                Map.of(INVESTOR_A, "100000000", INVESTOR_B, "50000000"), "0", "2027-01-04", "2027-12-30");

        assertProblem(result, 403, "/problems/forbidden");
        assertNoFacility(DEAL_NEW);
    }

    @Test
    @UseCase(id = "UC-015", requirements = {"NFR-007"})
    void rejects_a_request_without_a_valid_user() {
        assertProblem(mvc.get().uri("/api/deals/{id}/facility-formation", DEAL_NEW).exchange(),
                401, "/problems/unauthenticated");
        assertProblem(start(DEAL_NEW, LEAD_RETIRED), 401, "/problems/unauthenticated");
    }

    // ---------------------------------------------------------------- 入力の形

    @Test
    @UseCase(id = "UC-015", scenario = "A5: 持分、引き出せる期間、リード銀行の額が正しくない")
    void rejects_a_request_without_the_participation_fingerprint() {
        MvcTestResult result = mvc.put().uri("/api/deals/{id}/facility", DEAL_NEW)
                .header("X-Acting-User", String.valueOf(LEAD_USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"investorShares": [], "leadCommitmentAmount": 1,
                         "startDate": "2027-01-04", "endDate": "2027-12-30"}
                        """)
                .exchange();

        assertThat(result).hasStatus(400);
        assertThat(result).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertNoFacility(DEAL_NEW);
    }

    // ---------------------------------------------------------------- helpers

    private MvcTestResult start(long dealId, long userId) {
        return mvc.get().uri("/api/deals/{id}/facility-formation", dealId)
                .header("X-Acting-User", String.valueOf(userId))
                .exchange();
    }

    private String fingerprint(long dealId) {
        MvcTestResult result = start(dealId, LEAD_USER);
        assertThat(result).hasStatusOk();
        try {
            return com.jayway.jsonpath.JsonPath.read(
                    result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8),
                    "$.participationFingerprint");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private MvcTestResult form(long dealId, long userId, String fingerprint, Integer facilityVersion,
            Map<Long, String> investorShares, String leadAmount, String startDate, String endDate) {
        String shares = investorShares.entrySet().stream()
                .map(e -> "{\"organizationId\": %d, \"amount\": %s}".formatted(e.getKey(), e.getValue()))
                .collect(java.util.stream.Collectors.joining(", ", "[", "]"));
        String body = """
                {"participationFingerprint": "%s", "facilityVersion": %s, "investorShares": %s,
                 "leadCommitmentAmount": %s, "startDate": %s, "endDate": %s}
                """.formatted(fingerprint, facilityVersion, shares, leadAmount, quoted(startDate), quoted(endDate));
        return mvc.put().uri("/api/deals/{id}/facility", dealId)
                .header("X-Acting-User", String.valueOf(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private static String quoted(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private void assertProblem(MvcTestResult result, int status, String type) {
        assertThat(result).hasStatus(status);
        assertThat(result).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.type").asString().isEqualTo(type);
    }

    private void assertInvalid(MvcTestResult result, String... fields) {
        assertProblem(result, 400, "/problems/invalid-input");
        assertThat(result).bodyJson().extractingPath("$.errors[*].field").asArray()
                .containsExactlyInAnyOrder((Object[]) fields);
    }

    private void assertNoFacility(long dealId) {
        assertThat(jdbc.queryForObject("select count(*) from facility.facility where deal_id = ?",
                Integer.class, dealId)).isZero();
    }

    private Map<Long, Double> shares(long dealId) {
        Map<Long, Double> shares = new java.util.LinkedHashMap<>();
        jdbc.query("""
                select s.organization_id, s.amount from facility.facility_share s
                join facility.facility f on f.id = s.facility_id where f.deal_id = ?
                """, rs -> {
            shares.put(rs.getLong(1), rs.getBigDecimal(2).doubleValue());
        }, dealId);
        return shares;
    }

    private List<String> facilityPeriod(long dealId) {
        return jdbc.queryForObject("select start_date, end_date from facility.facility where deal_id = ?",
                (rs, n) -> List.of(rs.getDate(1).toLocalDate().toString(), rs.getDate(2).toLocalDate().toString()),
                dealId);
    }
}
