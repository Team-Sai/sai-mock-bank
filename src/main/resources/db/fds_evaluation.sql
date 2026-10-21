CREATE TABLE IF NOT EXISTS fds_evaluation (
                                              id BIGINT NOT NULL AUTO_INCREMENT,
                                              transaction_id BIGINT,
                                              sender_account_id BIGINT NOT NULL,
                                              receiver_account_id BIGINT NOT NULL,
                                              amount DECIMAL(19, 2) NOT NULL,
    risk_score INT NOT NULL,
    risk_level VARCHAR(20) NOT NULL,
    action VARCHAR(20) NOT NULL,
    matched_rules VARCHAR(255),
    reasons LONGTEXT,
    evaluated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_fds_transaction (transaction_id),
    INDEX idx_fds_evaluated_at (evaluated_at)
    );