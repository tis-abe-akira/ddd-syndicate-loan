-- 開発用のサンプルデータ(dev プロファイルだけ)。画面を試すために使う。
-- 本番のマイグレーションには入れない。id は 1000 番台に固定し、シーケンスとぶつからないようにする。
-- 何度流しても同じ結果になるよう、既にある行は飛ばす。

insert into organization.organization (id, name, kind, created_at)
values (1001, 'さくら銀行', 'FINANCIAL_INSTITUTION', timestamp '2026-01-05 09:00:00'),
       (1002, 'みなと信託銀行', 'FINANCIAL_INSTITUTION', timestamp '2026-01-05 09:00:00'),
       (1003, 'あおば生命保険', 'FINANCIAL_INSTITUTION', timestamp '2026-01-05 09:00:00'),
       (1004, 'ひかり地方銀行', 'FINANCIAL_INSTITUTION', timestamp '2026-01-05 09:00:00'),
       (1005, '東都電機工業', 'BORROWER', timestamp '2026-01-05 09:00:00')
on conflict (id) do nothing;

-- 利用者: さくら銀行(リード銀行)の担当者と組織管理者、みなと信託銀行(出資者)の担当者
insert into organization.app_user (id, organization_id, name, email, active)
values (1101, 1001, '佐藤 花子', 'hanako.sato@sakura-bank.example.com', true),
       (1102, 1001, '鈴木 一郎', 'ichiro.suzuki@sakura-bank.example.com', true),
       (1103, 1002, '高橋 健', 'ken.takahashi@minato-trust.example.com', true)
on conflict (id) do nothing;
insert into organization.user_role (user_id, role)
values (1101, 'BUSINESS_USER'),
       (1102, 'ORG_ADMIN'),
       (1103, 'BUSINESS_USER')
on conflict do nothing;

-- 融資案件: リード銀行はさくら銀行
insert into syndicate.deal (id, borrower_organization_id, lead_organization_id, purpose, requested_amount,
                            currency, requested_term_months, requested_repayment_method, status, applied_by,
                            applied_at)
values (1201, 1005, 1001, '新工場の設備投資', 3000000000, 'JPY', 84, 'EQUAL_INSTALLMENT', 'PREPARING', 1101, timestamp '2026-09-01 10:00:00'),
       (1202, 1005, 1001, '運転資金', 500000000, 'JPY', 36, 'BULLET_PAYMENT', 'PREPARING', 1101, timestamp '2026-09-15 10:00:00'),
       (1203, 1005, 1001, '研究開発センターの建設', 2000000000, 'JPY', 120, 'EQUAL_INSTALLMENT', 'PREPARING', 1101, timestamp '2026-08-01 10:00:00')
on conflict (id) do nothing;

-- 参加: 1201 は3社が応諾、1202 は応諾者なし、1203 は組成中のファシリティあり
insert into syndicate.deal_participant (id, deal_id, organization_id, role, status, commitment_amount)
values (1401, 1201, 1002, 'INVESTOR', 'ACCEPTED', 1200000000),
       (1402, 1201, 1003, 'INVESTOR', 'ACCEPTED', 800000000),
       (1403, 1201, 1004, 'INVESTOR', 'ACCEPTED', 500000000),
       (1404, 1202, 1002, 'INVESTOR', 'INVITED', null),
       (1405, 1203, 1002, 'INVESTOR', 'ACCEPTED', 1000000000),
       (1406, 1203, 1003, 'INVESTOR', 'ACCEPTED', 600000000)
on conflict (id) do nothing;

insert into facility.facility (id, deal_id, commitment_amount, currency, start_date, end_date, status,
                               approval_request_id, version)
values (1301, 1203, 1800000000, 'JPY', date '2027-04-01', date '2028-03-31', 'DRAFT', null, 0)
on conflict (id) do nothing;
insert into facility.facility_share (facility_id, organization_id, amount)
values (1301, 1002, 1000000000),
       (1301, 1003, 500000000),
       (1301, 1001, 300000000)
on conflict do nothing;
