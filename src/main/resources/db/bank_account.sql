CREATE TABLE IF NOT EXITSTSbank_account (
                                account_id bigint NOT NULL AUTO_INCREMENT,
                                bank_code varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
                                account_number varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
                                account_name varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
                                account_holder_name varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
                                balance decimal(19,2) NOT NULL DEFAULT '0.00',
                                status varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE',
                                created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                bank_user_id bigint NOT NULL,
                                PRIMARY KEY (account_id),
                                UNIQUE KEY uk_bank_account_number (account_number),
                                KEY fk_bank_account_identity (bank_user_id),
                                CONSTRAINT fk_bank_account_identity FOREIGN KEY (bank_user_id) REFERENCES bank_user (bank_user_id),
                                CONSTRAINT chk_bank_account_balance CHECK ((balance >= 0))
) ENGINE=InnoDB AUTO_INCREMENT=18 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci

CREATE INDEX idx_bank_account_user_key
    ON bank_account (user_key);
