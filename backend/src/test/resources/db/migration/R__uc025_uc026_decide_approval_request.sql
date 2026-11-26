-- UC-025 稟議に合議する、UC-026 稟議を承認する: API テストのテストデータ。id は 6000 番台に固定する。

insert into organization.organization (id, name, kind, created_at)
values (6001, 'UC025 銀行', 'FINANCIAL_INSTITUTION', timestamp '2026-01-05 09:00:00'),
       (6002, 'UC025 他行', 'FINANCIAL_INSTITUTION', timestamp '2026-01-05 09:00:00'),
       (6003, 'UC025 借り手', 'BORROWER', timestamp '2026-01-05 09:00:00'),
       (6004, 'UC025 出資者', 'FINANCIAL_INSTITUTION', timestamp '2026-01-05 09:00:00');

insert into organization.app_user (id, organization_id, name, email, active)
values (6101, 6001, '起案者', 'drafter@uc025.example.com', true),
       (6102, 6001, '合議 A', 'consult.a@uc025.example.com', true),
       (6103, 6001, '合議 B', 'consult.b@uc025.example.com', true),
       (6104, 6001, '承認 1段目', 'main1@uc025.example.com', true),
       (6105, 6001, '承認 2段目', 'main2@uc025.example.com', true),
       (6106, 6001, '報告先', 'report@uc025.example.com', true),
       (6107, 6001, '組織管理者', 'admin@uc025.example.com', true),
       (6110, 6002, '他行の利用者', 'other@uc025.example.com', true),
       (6111, 6004, '出資者の担当者', 'investor@uc025.example.com', true);
insert into organization.user_role (user_id, role)
values (6101, 'BUSINESS_USER'), (6102, 'BUSINESS_USER'), (6103, 'BUSINESS_USER'), (6104, 'BUSINESS_USER'),
       (6105, 'BUSINESS_USER'), (6106, 'BUSINESS_USER'), (6107, 'ORG_ADMIN'), (6110, 'BUSINESS_USER'),
       (6111, 'BUSINESS_USER');

insert into workflow.approval_request (id, organization_id, subject_type, subject_ref, title, body, drafter_id,
                                       status, round, submitted_at, decided_at, version)
values (6201, 6001, 'UC025_TYPE', 'deal:1', '合議中の稟議', '本文', 6101, 'IN_CONSULTATION', 1,
        timestamp '2026-11-30 10:00:00', null, 0),
       (6202, 6001, 'UC025_TYPE', 'deal:2', '承認中の稟議', null, 6101, 'IN_APPROVAL', 1,
        timestamp '2026-11-30 10:00:00', null, 1),
       (6203, 6001, 'UC025_TYPE', 'deal:3', '承認済の稟議', null, 6101, 'APPROVED', 1,
        timestamp '2026-11-30 10:00:00', timestamp '2026-11-30 12:00:00', 2),
       (6204, 6002, 'UC025_TYPE', 'deal:4', '他行の稟議', null, 6110, 'IN_APPROVAL', 1,
        timestamp '2026-11-30 10:00:00', null, 0),
       (6205, 6001, 'FACILITY_FORMATION', 'facility:6601', 'ファシリティ組成', null, 6101, 'IN_APPROVAL', 1,
        timestamp '2026-11-30 10:00:00', null, 0),
       (6206, 6001, 'FACILITY_FORMATION', 'facility:6602', '稟議にかかっていないファシリティ', null, 6101,
        'IN_APPROVAL', 1, timestamp '2026-11-30 10:00:00', null, 0),
       (6207, 6001, 'FACILITY_FORMATION', 'facility:6603', '合議中の組成の稟議', null, 6101, 'IN_CONSULTATION',
        1, timestamp '2026-11-30 10:00:00', null, 0),
       (6208, 6001, 'FACILITY_FORMATION', 'facility:6604', '合議中の組成の稟議(否決)', null, 6101,
        'IN_CONSULTATION', 1, timestamp '2026-11-30 10:00:00', null, 0),
       (6209, 6001, 'UC025_TYPE', 'deal:9', '再申請された合議中の稟議', null, 6101, 'IN_CONSULTATION', 2,
        timestamp '2026-11-30 15:00:00', null, 3),
       (6210, 6001, 'UC025_TYPE', 'deal:10', '再申請された承認中の稟議', null, 6101, 'IN_APPROVAL', 2,
        timestamp '2026-11-30 15:00:00', null, 2);
insert into workflow.approval_attribute (request_id, name, value)
values (6201, 'amount', 500000000);
insert into workflow.approval_condition (request_id, name, value)
values (6201, 'currency', 'JPY');

-- 6201: 合議 A・B → 承認 1段目 → 承認 2段目、報告先
-- 6202: 合議 A は承認済み、承認 1段目の番
insert into workflow.approval_step (id, request_id, round, kind, step_order, actor_id, auto_assigned, decision,
                                    comment, decided_at)
values (6301, 6201, 1, 'CONSULTATION', null, 6102, true, 'PENDING', null, null),
       (6302, 6201, 1, 'CONSULTATION', null, 6103, true, 'PENDING', null, null),
       (6303, 6201, 1, 'MAIN', 1, 6104, true, 'PENDING', null, null),
       (6304, 6201, 1, 'MAIN', 2, 6105, true, 'PENDING', null, null),
       (6305, 6201, 1, 'REPORT', null, 6106, true, 'PENDING', null, null),
       (6311, 6202, 1, 'CONSULTATION', null, 6102, true, 'APPROVED', '異議なし', timestamp '2026-11-30 11:00:00'),
       (6312, 6202, 1, 'MAIN', 1, 6104, true, 'PENDING', null, null),
       (6313, 6202, 1, 'MAIN', 2, 6105, true, 'PENDING', null, null),
       (6314, 6202, 1, 'REPORT', null, 6106, true, 'PENDING', null, null),
       (6321, 6203, 1, 'MAIN', 1, 6104, true, 'APPROVED', null, timestamp '2026-11-30 12:00:00'),
       (6331, 6204, 1, 'MAIN', 1, 6110, true, 'PENDING', null, null),
       (6341, 6205, 1, 'MAIN', 1, 6104, true, 'PENDING', null, null),
       (6351, 6206, 1, 'MAIN', 1, 6104, true, 'PENDING', null, null),
       (6361, 6207, 1, 'CONSULTATION', null, 6102, true, 'PENDING', null, null),
       (6362, 6207, 1, 'MAIN', 1, 6104, true, 'PENDING', null, null),
       (6371, 6208, 1, 'CONSULTATION', null, 6102, true, 'PENDING', null, null),
       (6372, 6208, 1, 'MAIN', 1, 6104, true, 'PENDING', null, null),
       -- 6209: 回 1 で合議 A が承認し(version 1)、合議 B が差し戻し(version 2)、再申請で回 2 になった(version 3)
       (6381, 6209, 1, 'CONSULTATION', null, 6102, true, 'APPROVED', null, timestamp '2026-11-30 11:00:00'),
       (6382, 6209, 1, 'CONSULTATION', null, 6103, true, 'SENT_BACK', '直してください',
        timestamp '2026-11-30 12:00:00'),
       (6383, 6209, 1, 'MAIN', 1, 6104, true, 'SKIPPED', null, null),
       (6384, 6209, 2, 'CONSULTATION', null, 6103, true, 'PENDING', null, null),
       (6385, 6209, 2, 'MAIN', 1, 6104, true, 'PENDING', null, null),
       -- 6210: 回 1 で承認 2段目が差し戻し(version 1)、再申請で回 2 になった(version 2)
       (6391, 6210, 1, 'MAIN', 1, 6105, true, 'SENT_BACK', '直してください', timestamp '2026-11-30 12:00:00'),
       (6392, 6210, 2, 'MAIN', 1, 6105, true, 'PENDING', null, null);

-- 組成の稟議の対象: 6501(組成中)のファシリティ 6601 は稟議 6205 にかかっている。
-- 6502(組成中)のファシリティ 6602 は、稟議 6206 にかかっていない(UC-026 A8)。
-- 6503・6504(組成中)のファシリティ 6603・6604 は、合議中の稟議 6207・6208 にかかっている(UC-025 BR-008)。
insert into syndicate.deal (id, borrower_organization_id, lead_organization_id, purpose, requested_amount,
                            currency, requested_term_months, requested_repayment_method, status, applied_by,
                            applied_at)
values (6501, 6003, 6001, '設備投資', 300000000, 'JPY', 60, 'EQUAL_INSTALLMENT', 'FORMING', 6101,
        timestamp '2026-09-01 10:00:00'),
       (6502, 6003, 6001, '運転資金', 100000000, 'JPY', 36, 'BULLET_PAYMENT', 'FORMING', 6101,
        timestamp '2026-09-01 10:00:00'),
       (6503, 6003, 6001, '設備投資', 100000000, 'JPY', 36, 'BULLET_PAYMENT', 'FORMING', 6101,
        timestamp '2026-09-01 10:00:00'),
       (6504, 6003, 6001, '設備投資', 100000000, 'JPY', 36, 'BULLET_PAYMENT', 'FORMING', 6101,
        timestamp '2026-09-01 10:00:00');
insert into syndicate.deal_participant (id, deal_id, organization_id, role, status, commitment_amount)
values (6401, 6501, 6004, 'INVESTOR', 'ACCEPTED', 200000000);
insert into facility.facility (id, deal_id, commitment_amount, currency, start_date, end_date, status,
                               approval_request_id, version)
values (6601, 6501, 300000000, 'JPY', date '2027-04-01', date '2028-03-31', 'DRAFT', 6205, 1),
       (6602, 6502, 100000000, 'JPY', date '2027-04-01', date '2028-03-31', 'DRAFT', null, 0),
       (6603, 6503, 100000000, 'JPY', date '2027-04-01', date '2028-03-31', 'DRAFT', 6207, 1),
       (6604, 6504, 100000000, 'JPY', date '2027-04-01', date '2028-03-31', 'DRAFT', 6208, 1);
insert into facility.facility_share (facility_id, organization_id, amount)
values (6601, 6001, 100000000),
       (6601, 6004, 200000000),
       (6602, 6001, 100000000),
       (6603, 6001, 100000000),
       (6604, 6001, 100000000);
