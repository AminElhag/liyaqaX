CREATE TABLE notifications (
    id                  BIGSERIAL PRIMARY KEY,
    public_id           UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    recipient_user_id   VARCHAR(100) NOT NULL,
    recipient_scope     VARCHAR(20) NOT NULL,
    type                VARCHAR(60) NOT NULL,
    title_key           VARCHAR(100) NOT NULL,
    body_key            VARCHAR(100) NOT NULL,
    params_json         TEXT,
    entity_type         VARCHAR(100),
    entity_id           VARCHAR(100),
    read_at             TIMESTAMPTZ,
    email_sent_at       TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notifications_recipient  ON notifications(recipient_user_id, read_at);
CREATE INDEX idx_notifications_type       ON notifications(type);
CREATE INDEX idx_notifications_created_at ON notifications(created_at DESC);
CREATE INDEX idx_notifications_entity     ON notifications(entity_type, entity_id);
