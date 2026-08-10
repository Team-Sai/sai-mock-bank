CREATE TABLE bank_transfer (
                               transfer_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                               request_key VARCHAR(100) NOT NULL,
                               from_account_id BIGINT NOT NULL,
                               to_account_id BIGINT NOT NULL,
                               amount DECIMAL(19, 2) NOT NULL,
                               sender_memo VARCHAR(100),
                               receiver_memo VARCHAR(100),
                               status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                               failure_reason VARCHAR(255),
                               completed_at DATETIME,
                               created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                                   ON UPDATE CURRENT_TIMESTAMP,

                               CONSTRAINT uk_bank_transfer_request_key
                                   UNIQUE (request_key),

                               CONSTRAINT fk_bank_transfer_from_account
                                   FOREIGN KEY (from_account_id)
                                       REFERENCES bank_account (account_id),

                               CONSTRAINT fk_bank_transfer_to_account
                                   FOREIGN KEY (to_account_id)
                                       REFERENCES bank_account (account_id),

                               CONSTRAINT chk_bank_transfer_amount
                                   CHECK (amount > 0),

                               CONSTRAINT chk_bank_transfer_different_account
                                   CHECK (from_account_id <> to_account_id)
)
    ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_bank_transfer_from_account
    ON bank_transfer (from_account_id);

CREATE INDEX idx_bank_transfer_to_account
    ON bank_transfer (to_account_id);
