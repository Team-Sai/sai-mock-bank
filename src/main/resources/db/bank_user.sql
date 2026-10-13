CREATE TABLE IF NOT EXISTS bank_user (
                             bank_user_id bigint NOT NULL AUTO_INCREMENT,
                             name varchar(50) NOT NULL,
                             issued_at datetime DEFAULT NULL,
                             created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
                             updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                             user_token varchar(255) NOT NULL,
                             user_key_hash varchar(50) DEFAULT NULL,
                             birth_date date NOT NULL,
                             email varchar(255) NOT NULL,
                             `PASSWORD` varchar(255) DEFAULT NULL,
                             PRIMARY KEY (bank_user_id),
                             UNIQUE KEY user_token (user_token),
                             UNIQUE KEY uk_bank_user_email (email)
) ENGINE=InnoDB AUTO_INCREMENT=24 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE bank_user
    ADD COLUMN IF NOT EXISTS pending_user_key    VARCHAR(255) NULL;
ALTER TABLE bank_user
    ADD COLUMN IF NOT EXISTS key_status          VARCHAR(20)  NULL DEFAULT NULL;
ALTER TABLE bank_user
    ADD COLUMN IF NOT EXISTS pending_issued_at   DATETIME     NULL;
ALTER TABLE bank_user
    ADD COLUMN IF NOT EXISTS pending_expires_at  DATETIME     NULL;
ALTER TABLE bank_user
    ADD COLUMN IF NOT EXISTS recovery_previous_key VARCHAR(255) NULL;
ALTER TABLE bank_user
    ADD COLUMN IF NOT EXISTS recovery_expires_at DATETIME(6) NULL;
ALTER TABLE bank_user
    ADD COLUMN IF NOT EXISTS rotation_operation_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL;
ALTER TABLE bank_user
    ADD COLUMN IF NOT EXISTS rotation_key_hash VARCHAR(255) NULL;

-- Keep completed operations independently of the user's latest rotation.
CREATE TABLE IF NOT EXISTS bank_key_operation (
    bank_user_id BIGINT NOT NULL,
    operation_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    key_hash VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    previous_key_hash VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NULL,
    issued_at DATETIME(6) NULL,
    recovered BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (bank_user_id, operation_id),
    CONSTRAINT fk_key_operation_user FOREIGN KEY (bank_user_id)
        REFERENCES bank_user(bank_user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
