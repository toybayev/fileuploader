CREATE TABLE IF NOT EXISTS idempotency_records (
    id bigserial PRIMARY KEY,
    user_id bigint NOT NULL,
    idempotency_key UUID NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'FAILED')),
    file_id bigint,
    response_json JSONB,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);


ALTER TABLE idempotency_records
    ADD CONSTRAINT uq_user_idempotency
        UNIQUE (user_id, idempotency_key);


ALTER TABLE idempotency_records
    ADD CONSTRAINT fk_idempotency_file
        FOREIGN KEY (file_id)
            REFERENCES files(id)
            ON DELETE SET NULL;


CREATE OR REPLACE FUNCTION update_updated_at_column()
    RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_idempotency_updated_at
    BEFORE UPDATE ON idempotency_records
    FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();
