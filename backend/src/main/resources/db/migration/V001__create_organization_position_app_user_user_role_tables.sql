-- organization モジュールの表(ADR-003)。シンジケートローンの知識を持たない。
CREATE SCHEMA IF NOT EXISTS organization;

CREATE SEQUENCE organization.organization_seq START WITH 1 INCREMENT BY 1 CACHE 50;

CREATE TABLE organization.organization
(
    id         BIGINT DEFAULT nextval('organization.organization_seq') PRIMARY KEY,
    name       VARCHAR(200) NOT NULL,
    kind       VARCHAR(30)  NOT NULL,
    created_at TIMESTAMP    NOT NULL,
    version    INTEGER      NOT NULL DEFAULT 0,
    -- UC-001 BR-001: A company name is registered only once.
    CONSTRAINT organization_name_uk UNIQUE (name),
    CONSTRAINT organization_kind_ck CHECK (kind IN ('OPERATOR', 'FINANCIAL_INSTITUTION', 'BORROWER'))
);

CREATE SEQUENCE organization.position_seq START WITH 1 INCREMENT BY 1 CACHE 50;

CREATE TABLE organization.position
(
    id              BIGINT DEFAULT nextval('organization.position_seq') PRIMARY KEY,
    organization_id BIGINT      NOT NULL,
    name            VARCHAR(50) NOT NULL,
    rank            INTEGER     NOT NULL,
    version         INTEGER     NOT NULL DEFAULT 0,
    CONSTRAINT position_organization_id_fk FOREIGN KEY (organization_id) REFERENCES organization.organization (id),
    CONSTRAINT position_rank_ck CHECK (rank BETWEEN 1 AND 99),
    CONSTRAINT position_organization_id_name_uk UNIQUE (organization_id, name),
    CONSTRAINT position_organization_id_rank_uk UNIQUE (organization_id, rank)
);

CREATE SEQUENCE organization.app_user_seq START WITH 1 INCREMENT BY 1 CACHE 50;

CREATE TABLE organization.app_user
(
    id              BIGINT DEFAULT nextval('organization.app_user_seq') PRIMARY KEY,
    organization_id BIGINT       NOT NULL,
    idp_subject     VARCHAR(255),
    name            VARCHAR(100) NOT NULL,
    email           VARCHAR(255) NOT NULL,
    position_id     BIGINT,
    active          BOOLEAN      NOT NULL,
    version         INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT app_user_organization_id_fk FOREIGN KEY (organization_id) REFERENCES organization.organization (id),
    CONSTRAINT app_user_position_id_fk FOREIGN KEY (position_id) REFERENCES organization.position (id),
    CONSTRAINT app_user_idp_subject_uk UNIQUE (idp_subject),
    -- UC-001 BR-004: An email address is used by at most one user across all organizations.
    CONSTRAINT app_user_email_uk UNIQUE (email)
);

CREATE TABLE organization.user_role
(
    user_id BIGINT      NOT NULL,
    role    VARCHAR(30) NOT NULL,
    CONSTRAINT user_role_pk PRIMARY KEY (user_id, role),
    CONSTRAINT user_role_user_id_fk FOREIGN KEY (user_id) REFERENCES organization.app_user (id),
    CONSTRAINT user_role_role_ck CHECK (role IN ('OPERATIONS_ADMIN', 'ORG_ADMIN', 'BUSINESS_USER',
                                                 'BORROWER_APPLICANT', 'BORROWER_VIEWER'))
);
