-- workflow モジュールの表(ADR-002)。業務の表への外部キーは持たない。利用組織・利用者・職位は organization を参照する。
CREATE SCHEMA IF NOT EXISTS workflow;

CREATE SEQUENCE workflow.route_rule_seq START WITH 1 INCREMENT BY 1 CACHE 50;

CREATE TABLE workflow.route_rule
(
    id              BIGINT DEFAULT nextval('workflow.route_rule_seq') PRIMARY KEY,
    organization_id BIGINT       NOT NULL,
    subject_type    VARCHAR(50)  NOT NULL,
    name            VARCHAR(100) NOT NULL,
    priority        INTEGER      NOT NULL,
    version         INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT route_rule_organization_id_fk FOREIGN KEY (organization_id) REFERENCES organization.organization (id),
    CONSTRAINT route_rule_priority_ck CHECK (priority BETWEEN 1 AND 9999),
    -- UC-024 BR-001: Among matching rules, the one with the smallest priority is chosen.
    CONSTRAINT route_rule_organization_id_subject_type_priority_uk UNIQUE (organization_id, subject_type, priority)
);

CREATE SEQUENCE workflow.route_rule_condition_seq START WITH 1 INCREMENT BY 1 CACHE 50;

CREATE TABLE workflow.route_rule_condition
(
    id             BIGINT DEFAULT nextval('workflow.route_rule_condition_seq') PRIMARY KEY,
    route_rule_id  BIGINT         NOT NULL,
    attribute_name VARCHAR(50)    NOT NULL,
    min_value      NUMERIC(19, 4),
    max_value      NUMERIC(19, 4),
    CONSTRAINT route_rule_condition_route_rule_id_fk FOREIGN KEY (route_rule_id) REFERENCES workflow.route_rule (id),
    CONSTRAINT route_rule_condition_bound_ck CHECK (min_value IS NOT NULL OR max_value IS NOT NULL),
    CONSTRAINT route_rule_condition_range_ck CHECK (min_value IS NULL OR max_value IS NULL OR min_value < max_value),
    CONSTRAINT route_rule_condition_route_rule_id_attribute_name_uk UNIQUE (route_rule_id, attribute_name)
);

CREATE SEQUENCE workflow.route_rule_step_seq START WITH 1 INCREMENT BY 1 CACHE 50;

CREATE TABLE workflow.route_rule_step
(
    id              BIGINT DEFAULT nextval('workflow.route_rule_step_seq') PRIMARY KEY,
    route_rule_id   BIGINT      NOT NULL,
    kind            VARCHAR(20) NOT NULL,
    step_order      INTEGER,
    position_id     BIGINT,
    default_user_id BIGINT,
    CONSTRAINT route_rule_step_route_rule_id_fk FOREIGN KEY (route_rule_id) REFERENCES workflow.route_rule (id),
    CONSTRAINT route_rule_step_position_id_fk FOREIGN KEY (position_id) REFERENCES organization.position (id),
    CONSTRAINT route_rule_step_default_user_id_fk FOREIGN KEY (default_user_id) REFERENCES organization.app_user (id),
    CONSTRAINT route_rule_step_kind_ck CHECK (kind IN ('CONSULTATION', 'MAIN', 'REPORT')),
    CONSTRAINT route_rule_step_step_order_ck CHECK (step_order BETWEEN 1 AND 99),
    CONSTRAINT route_rule_step_main_ck CHECK (
        (kind = 'MAIN' AND step_order IS NOT NULL AND position_id IS NOT NULL)
            OR (kind <> 'MAIN' AND step_order IS NULL)),
    CONSTRAINT route_rule_step_assignee_ck CHECK (position_id IS NOT NULL OR default_user_id IS NOT NULL),
    CONSTRAINT route_rule_step_route_rule_id_step_order_uk UNIQUE (route_rule_id, step_order)
);

CREATE SEQUENCE workflow.approval_request_seq START WITH 1 INCREMENT BY 1 CACHE 50;

CREATE TABLE workflow.approval_request
(
    id                BIGINT DEFAULT nextval('workflow.approval_request_seq') PRIMARY KEY,
    organization_id   BIGINT       NOT NULL,
    subject_type      VARCHAR(50)  NOT NULL,
    subject_ref       VARCHAR(100) NOT NULL,
    title             VARCHAR(200) NOT NULL,
    body              VARCHAR(4000),
    drafter_id        BIGINT       NOT NULL,
    route_rule_id     BIGINT,
    redrafted_from_id BIGINT,
    status            VARCHAR(20)  NOT NULL,
    round             INTEGER      NOT NULL,
    submitted_at      TIMESTAMP,
    decided_at        TIMESTAMP,
    version           INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT approval_request_organization_id_fk FOREIGN KEY (organization_id) REFERENCES organization.organization (id),
    CONSTRAINT approval_request_drafter_id_fk FOREIGN KEY (drafter_id) REFERENCES organization.app_user (id),
    CONSTRAINT approval_request_route_rule_id_fk FOREIGN KEY (route_rule_id) REFERENCES workflow.route_rule (id),
    CONSTRAINT approval_request_redrafted_from_id_fk
        FOREIGN KEY (redrafted_from_id) REFERENCES workflow.approval_request (id),
    CONSTRAINT approval_request_redrafted_from_id_uk UNIQUE (redrafted_from_id),
    CONSTRAINT approval_request_status_ck
        CHECK (status IN ('DRAFT', 'IN_CONSULTATION', 'IN_APPROVAL', 'APPROVED', 'REJECTED')),
    CONSTRAINT approval_request_round_ck CHECK (round BETWEEN 0 AND 999),
    CONSTRAINT approval_request_decided_at_ck
        CHECK ((status IN ('APPROVED', 'REJECTED')) = (decided_at IS NOT NULL))
);

-- UC-024 BR-009: At most one request per subject is in progress or sent back at a time.
CREATE UNIQUE INDEX approval_request_open_subject_uk
    ON workflow.approval_request (organization_id, subject_type, subject_ref)
    WHERE status IN ('DRAFT', 'IN_CONSULTATION', 'IN_APPROVAL');

CREATE TABLE workflow.approval_attribute
(
    request_id BIGINT         NOT NULL,
    name       VARCHAR(50)    NOT NULL,
    value      NUMERIC(19, 4) NOT NULL,
    CONSTRAINT approval_attribute_pk PRIMARY KEY (request_id, name),
    CONSTRAINT approval_attribute_request_id_fk FOREIGN KEY (request_id) REFERENCES workflow.approval_request (id)
);

CREATE TABLE workflow.approval_condition
(
    request_id BIGINT       NOT NULL,
    name       VARCHAR(50)  NOT NULL,
    value      VARCHAR(500) NOT NULL,
    CONSTRAINT approval_condition_pk PRIMARY KEY (request_id, name),
    CONSTRAINT approval_condition_request_id_fk FOREIGN KEY (request_id) REFERENCES workflow.approval_request (id)
);

CREATE SEQUENCE workflow.approval_step_seq START WITH 1 INCREMENT BY 1 CACHE 50;

CREATE TABLE workflow.approval_step
(
    id            BIGINT DEFAULT nextval('workflow.approval_step_seq') PRIMARY KEY,
    request_id    BIGINT      NOT NULL,
    round         INTEGER     NOT NULL,
    kind          VARCHAR(20) NOT NULL,
    step_order    INTEGER,
    position_id   BIGINT,
    actor_id      BIGINT      NOT NULL,
    auto_assigned BOOLEAN     NOT NULL,
    decision      VARCHAR(20) NOT NULL,
    comment       VARCHAR(2000),
    decided_at    TIMESTAMP,
    CONSTRAINT approval_step_request_id_fk FOREIGN KEY (request_id) REFERENCES workflow.approval_request (id),
    CONSTRAINT approval_step_position_id_fk FOREIGN KEY (position_id) REFERENCES organization.position (id),
    CONSTRAINT approval_step_actor_id_fk FOREIGN KEY (actor_id) REFERENCES organization.app_user (id),
    CONSTRAINT approval_step_round_ck CHECK (round BETWEEN 1 AND 999),
    CONSTRAINT approval_step_kind_ck CHECK (kind IN ('CONSULTATION', 'MAIN', 'REPORT')),
    CONSTRAINT approval_step_step_order_ck CHECK (step_order BETWEEN 1 AND 99),
    CONSTRAINT approval_step_main_ck CHECK ((kind = 'MAIN') = (step_order IS NOT NULL)),
    CONSTRAINT approval_step_decision_ck
        CHECK (decision IN ('PENDING', 'APPROVED', 'SENT_BACK', 'REJECTED', 'NOTIFIED', 'SKIPPED')),
    CONSTRAINT approval_step_report_decision_ck
        CHECK ((kind = 'REPORT' AND decision IN ('PENDING', 'NOTIFIED'))
            OR (kind <> 'REPORT' AND decision <> 'NOTIFIED')),
    CONSTRAINT approval_step_decided_at_ck
        CHECK ((decision IN ('PENDING', 'SKIPPED')) = (decided_at IS NULL)),
    CONSTRAINT approval_step_request_id_round_step_order_uk UNIQUE (request_id, round, step_order)
);
