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
                             PASSWORD varchar(255) DEFAULT NULL,
                             PRIMARY KEY (bank_user_id),
                             UNIQUE KEY user_token (user_token),
                             UNIQUE KEY uk_bank_user_email (email)
) ENGINE=InnoDB AUTO_INCREMENT=24 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
