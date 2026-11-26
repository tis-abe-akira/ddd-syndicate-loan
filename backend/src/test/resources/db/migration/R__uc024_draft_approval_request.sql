-- UC-024 稟議を起案する: サービスの層のテストのテストデータ。id は 7000 番台に固定する。

insert into organization.organization (id, name, kind, created_at)
values (7001, 'UC024 銀行', 'FINANCIAL_INSTITUTION', timestamp '2026-01-05 09:00:00'),
       (7002, 'UC024 他行', 'FINANCIAL_INSTITUTION', timestamp '2026-01-05 09:00:00');

-- 職位: 担当(1) < 課長(2) < 部長(3) < 本部長(4)。他行にも部長がある
insert into organization.position (id, organization_id, name, rank)
values (7601, 7001, '担当', 1),
       (7602, 7001, '課長', 2),
       (7603, 7001, '部長', 3),
       (7604, 7001, '本部長', 4),
       (7611, 7002, '部長', 3);

insert into organization.app_user (id, organization_id, name, email, position_id, active)
values (7101, 7001, '起案 担当', 'drafter@uc024.example.com', 7601, true),
       (7102, 7001, '課長 A', 'manager.a@uc024.example.com', 7602, true),
       (7103, 7001, '課長 B', 'manager.b@uc024.example.com', 7602, true),
       (7104, 7001, '部長 A', 'director.a@uc024.example.com', 7603, true),
       (7105, 7001, '部長 B', 'director.b@uc024.example.com', 7603, true),
       (7106, 7001, '本部長', 'head@uc024.example.com', 7604, true),
       (7107, 7001, '審査 合議', 'review@uc024.example.com', 7602, true),
       (7108, 7001, '報告 管理', 'report@uc024.example.com', null, true),
       (7109, 7001, '退職 部長', 'retired@uc024.example.com', 7603, false),
       (7110, 7002, '他行 部長', 'other@uc024.example.com', 7611, true),
       (7111, 7001, '職位なし', 'nopos@uc024.example.com', null, true);
insert into organization.user_role (user_id, role)
select id, 'BUSINESS_USER' from organization.app_user where id between 7101 and 7111;

-- UC024_TYPE: 10億円未満は 7501(課長 → 部長)、10億円以上は 7502(課長 → 部長 → 本部長)。他行にも同じ種類の規則
insert into workflow.route_rule (id, organization_id, subject_type, name, priority)
values (7501, 7001, 'UC024_TYPE', '10億円未満', 10),
       (7502, 7001, 'UC024_TYPE', '10億円以上', 20),
       (7503, 7001, 'UC024_PRIO', '金額があれば', 5),
       (7504, 7001, 'UC024_PRIO', 'スコア80以上', 1),
       (7505, 7001, 'UC024_NOMAIN', 'メイントラックなし', 10),
       (7506, 7001, 'UC024_NOCONSULT', '合議なし', 10),
       (7507, 7002, 'UC024_TYPE', '他行の規則', 1),
       (7508, 7001, 'UC024_CONSULTPOS', '合議の段に職位あり', 10);
insert into workflow.route_rule_condition (id, route_rule_id, attribute_name, min_value, max_value)
values (7511, 7501, 'amount', null, 1000000000),
       (7512, 7502, 'amount', 1000000000, null),
       (7513, 7503, 'amount', 0, null),
       (7514, 7504, 'credit_score', 80, null);
insert into workflow.route_rule_step (id, route_rule_id, kind, step_order, position_id, default_user_id)
values (7521, 7501, 'CONSULTATION', null, null, 7107),
       (7522, 7501, 'MAIN', 1, 7602, 7102),
       (7523, 7501, 'MAIN', 2, 7603, null),
       (7524, 7501, 'REPORT', null, null, 7108),
       (7531, 7502, 'CONSULTATION', null, null, 7107),
       (7532, 7502, 'MAIN', 1, 7602, 7102),
       (7533, 7502, 'MAIN', 2, 7603, null),
       (7534, 7502, 'MAIN', 3, 7604, 7106),
       (7535, 7502, 'REPORT', null, null, 7108),
       (7541, 7503, 'MAIN', 1, 7602, 7102),
       (7542, 7504, 'MAIN', 1, 7603, 7104),
       (7551, 7505, 'CONSULTATION', null, null, 7107),
       (7561, 7506, 'MAIN', 1, 7602, 7102),
       (7571, 7507, 'MAIN', 1, 7611, 7110),
       (7581, 7508, 'CONSULTATION', null, 7603, null),
       (7582, 7508, 'MAIN', 1, 7602, 7102),
       (7583, 7508, 'REPORT', null, 7603, null);
