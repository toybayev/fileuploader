CREATE TABLE IF NOT EXISTS files(

    id bigserial primary key,
    user_id bigint NOT NULL ,
    original_filename varchar(255) not null,
    stored_filename VARCHAR(255) NOT NULL UNIQUE,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL CHECK (file_size > 0),

    storage_url TEXT NOT NULL,
    bucket_name VARCHAR(100) NOT NULL DEFAULT 'user-files',

    uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
)
