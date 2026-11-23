-- UC-015 ファシリティを組成する: API テストのテストデータ。
-- id は 9000 番台に固定し、本体のシーケンス(1 から)とぶつからないようにする。

-- 利用組織: リード銀行、出資者 A・B・C、他行、借り手
insert into organization.organization (id, name, kind, created_at)
values (9001, 'UC015 リード銀行', 'FINANCIAL_INSTITUTION', timestamp '2026-01-05 09:00:00'),
       (9002, 'UC015 出資者A', 'FINANCIAL_INSTITUTION', timestamp '2026-01-05 09:00:00'),
       (9003, 'UC015 出資者B', 'FINANCIAL_INSTITUTION', timestamp '2026-01-05 09:00:00'),
       (9004, 'UC015 出資者C', 'FINANCIAL_INSTITUTION', timestamp '2026-01-05 09:00:00'),
       (9005, 'UC015 他行', 'FINANCIAL_INSTITUTION', timestamp '2026-01-05 09:00:00'),
       (9006, 'UC015 借り手', 'BORROWER', timestamp '2026-01-05 09:00:00');

-- 利用者: リード銀行の業務ユーザー、リード銀行の組織管理者(業務ユーザーではない)、
-- リード銀行の無効な利用者、他行の業務ユーザー
insert into organization.app_user (id, organization_id, name, email, active)
values (9101, 9001, 'リード担当', 'lead.user@uc015.example.com', true),
       (9102, 9001, 'リード管理者', 'lead.admin@uc015.example.com', true),
       (9103, 9001, 'リード退職者', 'lead.retired@uc015.example.com', false),
       (9104, 9005, '他行担当', 'other.user@uc015.example.com', true);
insert into organization.user_role (user_id, role)
values (9101, 'BUSINESS_USER'),
       (9102, 'ORG_ADMIN'),
       (9103, 'BUSINESS_USER'),
       (9104, 'BUSINESS_USER');

-- 融資案件: すべてリード銀行 9001、借り手 9006、通貨 JPY
insert into syndicate.deal (id, borrower_organization_id, lead_organization_id, purpose, requested_amount,
                            currency, requested_term_months, requested_repayment_method, status, applied_by,
                            applied_at)
values (9201, 9006, 9001, '組成準備中・ファシリティなし', 150000000, 'JPY', 60, 'EQUAL_INSTALLMENT', 'PREPARING', 9101, timestamp '2026-02-01 10:00:00'),
       (9202, 9006, 9001, '応諾した出資者なし', 150000000, 'JPY', 60, 'EQUAL_INSTALLMENT', 'PREPARING', 9101, timestamp '2026-02-01 10:00:00'),
       (9203, 9006, 9001, '組成中のファシリティあり', 150000000, 'JPY', 60, 'EQUAL_INSTALLMENT', 'PREPARING', 9101, timestamp '2026-02-01 10:00:00'),
       (9204, 9006, 9001, 'FIXED のファシリティあり', 150000000, 'JPY', 60, 'EQUAL_INSTALLMENT', 'PREPARING', 9101, timestamp '2026-02-01 10:00:00'),
       (9205, 9006, 9001, '稟議中のファシリティあり', 150000000, 'JPY', 60, 'EQUAL_INSTALLMENT', 'PREPARING', 9101, timestamp '2026-02-01 10:00:00'),
       (9206, 9006, 9001, '取下げ', 150000000, 'JPY', 60, 'EQUAL_INSTALLMENT', 'WITHDRAWN', 9101, timestamp '2026-02-01 10:00:00'),
       (9207, 9006, 9001, '審査中', 150000000, 'JPY', 60, 'EQUAL_INSTALLMENT', 'SCREENING', 9101, timestamp '2026-02-01 10:00:00');

-- 参加者: 出資者 A は 1億円、B は 5千万円で応諾、C は招待中(応諾していない)
insert into syndicate.deal_participant (id, deal_id, organization_id, role, status, commitment_amount)
values (9401, 9201, 9002, 'INVESTOR', 'ACCEPTED', 100000000),
       (9402, 9201, 9003, 'INVESTOR', 'ACCEPTED', 50000000),
       (9403, 9201, 9004, 'INVESTOR', 'INVITED', null),
       (9404, 9202, 9004, 'INVESTOR', 'INVITED', null),
       (9405, 9203, 9002, 'INVESTOR', 'ACCEPTED', 100000000),
       (9406, 9203, 9003, 'INVESTOR', 'ACCEPTED', 50000000),
       (9407, 9204, 9002, 'INVESTOR', 'ACCEPTED', 100000000),
       (9408, 9205, 9002, 'INVESTOR', 'ACCEPTED', 100000000),
       (9409, 9206, 9002, 'INVESTOR', 'ACCEPTED', 100000000),
       (9410, 9207, 9002, 'INVESTOR', 'ACCEPTED', 100000000);

-- ファシリティ: 9203 は組成中(A 8千万円、リード 2千万円)、9204 は FIXED、9205 は組成中で稟議にかかっている
insert into facility.facility (id, deal_id, commitment_amount, currency, start_date, end_date, status,
                               approval_request_id, version)
values (9301, 9203, 100000000, 'JPY', date '2027-01-04', date '2027-12-30', 'DRAFT', null, 3),
       (9302, 9204, 100000000, 'JPY', date '2027-01-04', date '2027-12-30', 'FIXED', 8001, 5),
       (9303, 9205, 100000000, 'JPY', date '2027-01-04', date '2027-12-30', 'DRAFT', 8002, 2);
insert into facility.facility_share (facility_id, organization_id, amount)
values (9301, 9002, 80000000),
       (9301, 9001, 20000000),
       (9302, 9002, 100000000),
       (9303, 9002, 100000000);
