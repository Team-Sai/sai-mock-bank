CREATE TABLE IF NOT EXISTS identity_verification (
                                       identity_id BIGINT NOT NULL AUTO_INCREMENT,
                                       identity_verification_id VARCHAR(100) NOT NULL,
                                       user_id BIGINT NOT NULL,
                                       purpose VARCHAR(30) NOT NULL,
                                       status VARCHAR(20) NOT NULL,
                                       requested_at DATETIME NOT NULL,
                                       verified_at DATETIME DEFAULT NULL,
                                       expires_at DATETIME DEFAULT NULL,
                                       used_at DATETIME DEFAULT NULL,
                                       failure_reason VARCHAR(255) DEFAULT NULL,
                                       created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                       updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                       PRIMARY KEY (identity_id),
                                       UNIQUE KEY uk_identity_verification_id (identity_verification_id),
                                       KEY idx_identity_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;