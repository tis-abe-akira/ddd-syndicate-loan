-- syndicate モジュールの表。融資案件と、その参加者(リード銀行・出資者)。
CREATE SCHEMA IF NOT EXISTS syndicate;

CREATE SEQUENCE syndicate.deal_seq START WITH 1 INCREMENT BY 1 CACHE 50;

CREATE TABLE syndicate.deal
(
    id                         BIGINT DEFAULT nextval('syndicate.deal_seq') PRIMARY KEY,
    borrower_organization_id   BIGINT         NOT NULL,
    lead_organization_id       BIGINT         NOT NULL,
    purpose                    VARCHAR(2000)  NOT NULL,
    requested_amount           NUMERIC(19, 2) NOT NULL,
    currency                   VARCHAR(3)     NOT NULL,
    requested_term_months      INTEGER        NOT NULL,
    requested_repayment_method VARCHAR(30)    NOT NULL,
    status                     VARCHAR(30)    NOT NULL,
    applied_by                 BIGINT         NOT NULL,
    applied_at                 TIMESTAMP      NOT NULL,
    version                    INTEGER        NOT NULL DEFAULT 0,
    CONSTRAINT deal_borrower_organization_id_fk
        FOREIGN KEY (borrower_organization_id) REFERENCES organization.organization (id),
    CONSTRAINT deal_lead_organization_id_fk
        FOREIGN KEY (lead_organization_id) REFERENCES organization.organization (id),
    CONSTRAINT deal_applied_by_fk FOREIGN KEY (applied_by) REFERENCES organization.app_user (id),
    CONSTRAINT deal_requested_amount_ck CHECK (requested_amount BETWEEN 1 AND 999999999999999),
    CONSTRAINT deal_requested_term_months_ck CHECK (requested_term_months BETWEEN 1 AND 600),
    CONSTRAINT deal_requested_repayment_method_ck
        CHECK (requested_repayment_method IN ('EQUAL_INSTALLMENT', 'BULLET_PAYMENT')),
    CONSTRAINT deal_status_ck CHECK (status IN ('APPLIED', 'SCREENING', 'PREPARING', 'FORMING', 'FORMED',
                                                'DECLINED', 'WITHDRAWN'))
);

CREATE SEQUENCE syndicate.deal_participant_seq START WITH 1 INCREMENT BY 1 CACHE 50;

CREATE TABLE syndicate.deal_participant
(
    id                  BIGINT DEFAULT nextval('syndicate.deal_participant_seq') PRIMARY KEY,
    deal_id             BIGINT         NOT NULL,
    organization_id     BIGINT         NOT NULL,
    role                VARCHAR(20)    NOT NULL,
    status              VARCHAR(20)    NOT NULL,
    commitment_amount   NUMERIC(19, 2),
    approval_request_id BIGINT,
    version             INTEGER        NOT NULL DEFAULT 0,
    CONSTRAINT deal_participant_deal_id_fk FOREIGN KEY (deal_id) REFERENCES syndicate.deal (id),
    CONSTRAINT deal_participant_organization_id_fk
        FOREIGN KEY (organization_id) REFERENCES organization.organization (id),
    CONSTRAINT deal_participant_deal_id_organization_id_uk UNIQUE (deal_id, organization_id),
    CONSTRAINT deal_participant_role_ck CHECK (role IN ('LEAD_ARRANGER', 'INVESTOR')),
    CONSTRAINT deal_participant_status_ck CHECK (status IN ('CANDIDATE', 'INVITED', 'ACCEPTED', 'DECLINED')),
    CONSTRAINT deal_participant_commitment_amount_ck
        CHECK (commitment_amount IS NULL OR commitment_amount BETWEEN 1 AND 999999999999999),
    CONSTRAINT deal_participant_accepted_commitment_ck
        CHECK (status <> 'ACCEPTED' OR commitment_amount IS NOT NULL)
);

-- 一つの融資案件のリード銀行は一つだけ。
CREATE UNIQUE INDEX deal_participant_lead_arranger_uk
    ON syndicate.deal_participant (deal_id) WHERE role = 'LEAD_ARRANGER';
