-- facility モジュールの表。ファシリティと、そのシェアパイ(ADR-001)。
CREATE SCHEMA IF NOT EXISTS facility;

CREATE SEQUENCE facility.facility_seq START WITH 1 INCREMENT BY 1 CACHE 50;

CREATE TABLE facility.facility
(
    id                  BIGINT DEFAULT nextval('facility.facility_seq') PRIMARY KEY,
    deal_id             BIGINT         NOT NULL,
    commitment_amount   NUMERIC(15, 0) NOT NULL,
    currency            VARCHAR(3)     NOT NULL,
    start_date          DATE           NOT NULL,
    end_date            DATE           NOT NULL,
    status              VARCHAR(10)    NOT NULL,
    approval_request_id BIGINT,
    version             INTEGER        NOT NULL DEFAULT 0,
    CONSTRAINT facility_deal_id_fk FOREIGN KEY (deal_id) REFERENCES syndicate.deal (id),
    -- UC-015 BR-004: A deal has at most one facility.
    CONSTRAINT facility_deal_id_uk UNIQUE (deal_id),
    CONSTRAINT facility_commitment_amount_ck CHECK (commitment_amount BETWEEN 1 AND 999999999999999),
    CONSTRAINT facility_status_ck CHECK (status IN ('DRAFT', 'FIXED')),
    -- UC-015 BR-003: The last day of the drawdown period comes after the first day.
    CONSTRAINT facility_period_ck CHECK (end_date > start_date)
);

CREATE TABLE facility.facility_share
(
    facility_id     BIGINT         NOT NULL,
    organization_id BIGINT         NOT NULL,
    amount          NUMERIC(15, 0) NOT NULL,
    CONSTRAINT facility_share_pk PRIMARY KEY (facility_id, organization_id),
    CONSTRAINT facility_share_facility_id_fk FOREIGN KEY (facility_id) REFERENCES facility.facility (id),
    CONSTRAINT facility_share_organization_id_fk
        FOREIGN KEY (organization_id) REFERENCES organization.organization (id),
    -- UC-015 BR-001: Only participating institutions with a share above zero belong to the pie.
    CONSTRAINT facility_share_amount_ck CHECK (amount BETWEEN 1 AND 999999999999999)
);
