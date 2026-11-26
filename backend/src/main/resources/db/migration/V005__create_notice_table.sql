-- notification モジュールの表。利用者の画面に届くお知らせ。
CREATE SCHEMA IF NOT EXISTS notification;

CREATE SEQUENCE notification.notice_seq START WITH 1 INCREMENT BY 1 CACHE 50;

CREATE TABLE notification.notice
(
    id           BIGINT DEFAULT nextval('notification.notice_seq') PRIMARY KEY,
    recipient_id BIGINT        NOT NULL,
    kind         VARCHAR(30)   NOT NULL,
    subject_ref  VARCHAR(100),
    message      VARCHAR(1000) NOT NULL,
    created_at   TIMESTAMP     NOT NULL,
    read_at      TIMESTAMP,
    CONSTRAINT notice_recipient_id_fk FOREIGN KEY (recipient_id) REFERENCES organization.app_user (id),
    CONSTRAINT notice_kind_ck CHECK (kind IN ('CONSULTATION_REQUESTED', 'APPROVAL_REQUESTED', 'SENT_BACK', 'REJECTED',
                                              'REPORTED', 'INVITED', 'OTHER'))
);
