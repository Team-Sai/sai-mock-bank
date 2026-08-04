DROP TABLE IF EXISTS bank_transaction;
DROP TABLE IF EXISTS bank_transfer;
DROP TABLE IF EXISTS bank_account;

CREATE TABLE bank_account (
                              account_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                              user_key VARCHAR(100) NOT NULL,
                              bank_code VARCHAR(20) NOT NULL,
                              account_number VARCHAR(30) NOT NULL,
                              account_name VARCHAR(100) NOT NULL,
                              account_holder_name VARCHAR(50) NOT NULL,
                              balance DECIMAL(19, 2) NOT NULL DEFAULT 0,
                              status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                              created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                                  ON UPDATE CURRENT_TIMESTAMP,

                              CONSTRAINT uk_bank_account_number
                                  UNIQUE (account_number),

                              CONSTRAINT chk_bank_account_balance
                                  CHECK (balance >= 0)
)
    ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_bank_account_user_key
    ON bank_account (user_key);


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


CREATE TABLE bank_transaction (
                                  transaction_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                  transaction_key VARCHAR(100) NOT NULL,
                                  transaction_type VARCHAR(20) NOT NULL,
                                  amount DECIMAL(19, 2) NOT NULL,
                                  balance_after DECIMAL(19, 2) NOT NULL,
                                  counterparty_name VARCHAR(50) NOT NULL,
                                  counterparty_account_number VARCHAR(30) NOT NULL,
                                  memo VARCHAR(100),
                                  transaction_at DATETIME NOT NULL,
                                  transfer_id BIGINT NOT NULL,
                                  account_id BIGINT NOT NULL,
                                  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                                      ON UPDATE CURRENT_TIMESTAMP,

                                  CONSTRAINT uk_bank_transaction_key
                                      UNIQUE (transaction_key),

                                  CONSTRAINT fk_bank_transaction_transfer
                                      FOREIGN KEY (transfer_id)
                                          REFERENCES bank_transfer (transfer_id),

                                  CONSTRAINT fk_bank_transaction_account
                                      FOREIGN KEY (account_id)
                                          REFERENCES bank_account (account_id),

                                  CONSTRAINT chk_bank_transaction_amount
                                      CHECK (amount > 0),

                                  CONSTRAINT chk_bank_transaction_balance
                                      CHECK (balance_after >= 0)
)
    ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4
COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_bank_transaction_account_date
    ON bank_transaction (account_id, transaction_at);

CREATE INDEX idx_bank_transaction_transfer
    ON bank_transaction (transfer_id);