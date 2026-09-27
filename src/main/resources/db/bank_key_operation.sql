CREATE TABLE IF NOT EXISTS bank_key_operation (
                                                  bank_user_id BIGINT NOT NULL,
                                                  operation_id VARCHAR(64)
    CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    key_hash VARCHAR(255)
    CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    previous_key_hash VARCHAR(255)
    CHARACTER SET ascii COLLATE ascii_bin NULL,
    issued_at DATETIME(6) NULL,
    recovered BOOLEAN NOT NULL DEFAULT FALSE,

    PRIMARY KEY (bank_user_id, operation_id),

    CONSTRAINT fk_key_operation_user
    FOREIGN KEY (bank_user_id)
    REFERENCES bank_user(bank_user_id)
    ON DELETE CASCADE
    ) ENGINE=InnoDB
    DEFAULT CHARSET=utf8mb4
    COLLATE=utf8mb4_unicode_ci;