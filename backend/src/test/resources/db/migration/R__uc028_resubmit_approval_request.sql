-- UC-028 差し戻された稟議を再申請する: サービスの層のテストのテストデータ。id は 8000 番台に固定する。

insert into organization.organization (id, name, kind, created_at)
values (8001, 'UC028 銀行', 'FINANCIAL_INSTITUTION', timestamp '2026-01-05 09:00:00'),
       (8002, 'UC028 他行', 'FINANCIAL_INSTITUTION', timestamp '2026-01-05 09:00:00');

-- 職位: 担当(1) < 課長(2) < 部長(3)
insert into organization.position (id, organization_id, name, rank)
values (8601, 8001, '担当', 1),
       (8602, 8001, '課長', 2),
       (8603, 8001, '部長', 3),
       (8611, 8002, '課長', 2);

insert into organization.app_user (id, organization_id, name, email, position_id, active)
values (8101, 8001, '起案 担当', 'drafter@uc028.example.com', 8601, true),
       (8102, 8001, '別の起案者', 'drafter2@uc028.example.com', 8601, true),
       (8103, 8001, '審査 合議', 'review@uc028.example.com', 8602, true),
       (8104, 8001, '課長', 'manager@uc028.example.com', 8602, true),
       (8105, 8001, '部長 A', 'director.a@uc028.example.com', 8603, true),
       (8106, 8001, '報告 管理', 'report@uc028.example.com', null, true),
       (8107, 8001, '部長 B', 'director.b@uc028.example.com', 8603, true),
       (8108, 8001, '組織管理者', 'admin@uc028.example.com', null, true),
       (8110, 8002, '他行 担当', 'other@uc028.example.com', 8611, true);
insert into organization.user_role (user_id, role)
values (8101, 'BUSINESS_USER'), (8102, 'BUSINESS_USER'), (8103, 'BUSINESS_USER'), (8104, 'BUSINESS_USER'),
       (8105, 'BUSINESS_USER'), (8106, 'BUSINESS_USER'), (8107, 'BUSINESS_USER'), (8108, 'ORG_ADMIN'),
       (8110, 'BUSINESS_USER');

-- UC028_TYPE: 1億円未満は 8501(合議 → 課長、報告)、1億円以上は 8502(課長 → 部長。部長の段に既定の利用者なし)
-- UC028_LIMITED: 1億円未満の 8503 だけ(1億円以上では合う規則がない)
insert into workflow.route_rule (id, organization_id, subject_type, name, priority)
values (8501, 8001, 'UC028_TYPE', '1億円未満', 10),
       (8502, 8001, 'UC028_TYPE', '1億円以上', 20),
       (8503, 8001, 'UC028_LIMITED', '1億円未満だけ', 10);
insert into workflow.route_rule_condition (id, route_rule_id, attribute_name, min_value, max_value)
values (8511, 8501, 'amount', null, 100000000),
       (8512, 8502, 'amount', 100000000, null),
       (8513, 8503, 'amount', null, 100000000);
insert into workflow.route_rule_step (id, route_rule_id, kind, step_order, position_id, default_user_id)
values (8521, 8501, 'CONSULTATION', null, null, 8103),
       (8522, 8501, 'MAIN', 1, 8602, 8104),
       (8523, 8501, 'REPORT', null, null, 8106),
       (8531, 8502, 'MAIN', 1, 8602, 8104),
       (8532, 8502, 'MAIN', 2, 8603, null),
       (8541, 8503, 'MAIN', 1, 8602, 8104);

-- 8201: 差し戻された稟議。回 1 で合議が承認し、課長が差し戻した(判断が2回なので version は 2)
-- 8202: 否決、8203: 承認中、8204: 差し戻し(UC028_LIMITED)、8205: 他行の差し戻し、8206: 別の起案者の差し戻し
insert into workflow.approval_request (id, organization_id, subject_type, subject_ref, title, body, drafter_id,
                                       route_rule_id, status, round, submitted_at, decided_at, version)
values (8201, 8001, 'UC028_TYPE', 'deal:1', '前の件名', '前の本文', 8101, 8501, 'DRAFT', 1,
        timestamp '2026-11-30 10:00:00', null, 2),
       (8202, 8001, 'UC028_TYPE', 'deal:2', '否決の稟議', null, 8101, 8501, 'REJECTED', 1,
        timestamp '2026-11-30 10:00:00', timestamp '2026-11-30 12:00:00', 1),
       (8203, 8001, 'UC028_TYPE', 'deal:3', '承認中の稟議', null, 8101, 8501, 'IN_APPROVAL', 1,
        timestamp '2026-11-30 10:00:00', null, 0),
       (8204, 8001, 'UC028_LIMITED', 'deal:4', '規則が限られた稟議', null, 8101, 8503, 'DRAFT', 1,
        timestamp '2026-11-30 10:00:00', null, 1),
       (8205, 8002, 'UC028_TYPE', 'deal:5', '他行の稟議', null, 8110, null, 'DRAFT', 1,
        timestamp '2026-11-30 10:00:00', null, 1),
       (8206, 8001, 'UC028_TYPE', 'deal:6', '別の起案者の稟議', null, 8102, 8501, 'DRAFT', 1,
        timestamp '2026-11-30 10:00:00', null, 1);
insert into workflow.approval_attribute (request_id, name, value)
values (8201, 'amount', 50000000);
insert into workflow.approval_condition (request_id, name, value)
values (8201, 'currency', 'JPY'),
       (8201, 'term_months', '36');

insert into workflow.approval_step (id, request_id, round, kind, step_order, position_id, actor_id, auto_assigned,
                                    decision, comment, decided_at)
values (8301, 8201, 1, 'CONSULTATION', null, null, 8103, true, 'APPROVED', '異議なし',
        timestamp '2026-11-30 11:00:00'),
       (8302, 8201, 1, 'MAIN', 1, 8602, 8104, true, 'SENT_BACK', '金額の根拠を足してください',
        timestamp '2026-11-30 12:00:00'),
       (8303, 8201, 1, 'REPORT', null, null, 8106, true, 'PENDING', null, null),
       (8311, 8202, 1, 'MAIN', 1, 8602, 8104, true, 'REJECTED', '見送り', timestamp '2026-11-30 12:00:00'),
       (8321, 8203, 1, 'MAIN', 1, 8602, 8104, true, 'PENDING', null, null),
       (8331, 8204, 1, 'MAIN', 1, 8602, 8104, true, 'SENT_BACK', '直してください',
        timestamp '2026-11-30 12:00:00'),
       (8341, 8205, 1, 'MAIN', 1, 8611, 8110, true, 'SENT_BACK', '直してください',
        timestamp '2026-11-30 12:00:00'),
       (8351, 8206, 1, 'MAIN', 1, 8602, 8104, true, 'SENT_BACK', '直してください',
        timestamp '2026-11-30 12:00:00');
