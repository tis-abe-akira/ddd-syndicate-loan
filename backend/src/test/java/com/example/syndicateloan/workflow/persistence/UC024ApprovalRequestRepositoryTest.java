package com.example.syndicateloan.workflow.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.syndicateloan.usecase.UseCase;
import com.example.syndicateloan.workflow.ApprovalConcurrentChangeException;
import com.example.syndicateloan.workflow.ApprovalStatus;
import com.example.syndicateloan.workflow.ApprovalSubject;
import com.example.syndicateloan.workflow.StepKind;
import com.example.syndicateloan.workflow.domain.ApprovalRequestRepository;
import com.example.syndicateloan.workflow.domain.NewApprovalRequest;
import com.example.syndicateloan.workflow.domain.RouteStep;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * UC-024 A6 のうち、サービスの層からは届かない分岐: 同じ対象の稟議が同時に申請され、一意索引が後の登録を拒む。
 * サービスは申請の前に同じ対象の稟議を確かめて A1 にするので、この分岐は二つの申請が同時に確かめを通ったときだけ通る。
 * 登録を直接二度呼んで、その場面を作る。各登録が自分のトランザクションで動くよう、テストはトランザクションの外で動かす。
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(UC024ApprovalRequestRepositoryTest.Config.class)
class UC024ApprovalRequestRepositoryTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class Config {

        @Bean
        @ServiceConnection
        PostgreSQLContainer postgres() {
            return new PostgreSQLContainer("postgres:17-alpine");
        }
    }

    private static final String SUBJECT_REF = "uc024-repository:1";

    @Autowired
    private ApprovalRequestRepository requests;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void removeCreatedRequests() {
        String ids = "(select id from workflow.approval_request where subject_ref = '" + SUBJECT_REF + "')";
        jdbc.update("delete from workflow.approval_step where request_id in " + ids);
        jdbc.update("delete from workflow.approval_attribute where request_id in " + ids);
        jdbc.update("delete from workflow.approval_condition where request_id in " + ids);
        jdbc.update("delete from workflow.approval_request where subject_ref = ?", SUBJECT_REF);
    }

    @Test
    @UseCase(id = "UC-024", scenario = "A6: 起案の間に規則か同じ対象の稟議が変わった", businessRules = {"BR-009"},
            requirements = {"NFR-003"})
    void rejects_a_second_request_for_the_same_subject_submitted_at_the_same_time() {
        requests.insert(request());

        assertThatThrownBy(() -> requests.insert(request())).isInstanceOf(ApprovalConcurrentChangeException.class);

        // 後の申請は、稟議も経路属性も承認経路も残らない
        assertThat(jdbc.queryForObject("select count(*) from workflow.approval_request where subject_ref = ?",
                Integer.class, SUBJECT_REF)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                select count(*) from workflow.approval_step s join workflow.approval_request r on r.id = s.request_id
                where r.subject_ref = ?""", Integer.class, SUBJECT_REF)).isEqualTo(1);
    }

    @Test
    @UseCase(id = "UC-024", scenario = "A5: 承認経路か件名が正しくない", requirements = {"NFR-003"})
    void leaves_neither_the_request_nor_its_route_when_writing_the_route_fails() {
        // 稟議の行を書いた後、承認経路の段の書き込みで失敗させる(存在しない利用者)
        NewApprovalRequest broken = new NewApprovalRequest(7001,
                new ApprovalSubject("UC024_TYPE", SUBJECT_REF, "件名", Map.of("amount", BigDecimal.ONE),
                        Map.of("currency", "JPY")),
                "件名", null, 7101, 7501, ApprovalStatus.IN_APPROVAL, LocalDateTime.of(2026, 12, 1, 9, 0),
                List.of(new RouteStep(StepKind.MAIN, 1, 7602L, 7102, true),
                        new RouteStep(StepKind.REPORT, null, null, 999_999, false)));

        assertThatThrownBy(() -> requests.insert(broken)).isInstanceOf(RuntimeException.class);

        // 失敗時の事後条件: 稟議と承認経路のどちらか一方だけが登録されることはない
        assertThat(jdbc.queryForObject("select count(*) from workflow.approval_request where subject_ref = ?",
                Integer.class, SUBJECT_REF)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from workflow.approval_attribute a "
                + "join workflow.approval_request r on r.id = a.request_id where r.subject_ref = ?",
                Integer.class, SUBJECT_REF)).isZero();
    }

    /** UC-024 のテストデータ(R__uc024_draft_approval_request.sql)の組織・起案者・規則で作る稟議。 */
    private static NewApprovalRequest request() {
        return new NewApprovalRequest(7001,
                new ApprovalSubject("UC024_TYPE", SUBJECT_REF, "件名", Map.of("amount", BigDecimal.ONE),
                        Map.of("currency", "JPY")),
                "件名", null, 7101, 7501, ApprovalStatus.IN_APPROVAL, LocalDateTime.of(2026, 12, 1, 9, 0),
                List.of(new RouteStep(StepKind.MAIN, 1, 7602L, 7102, true)));
    }
}
