CREATE TABLE IF NOT EXISTS bank_transaction (
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