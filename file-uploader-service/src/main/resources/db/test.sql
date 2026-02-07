UPDATE idempotency_records
SET status = 'IN_PROGRESS',
    created_at = NOW() - INTERVAL '6 minutes',
    updated_at = NOW() - INTERVAL '6 minutes'
WHERE file_id = 13;
