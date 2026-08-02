CREATE TABLE `bank_identity` (
                                 `identity_id` bigint NOT NULL AUTO_INCREMENT,
                                 `name` varchar(50) NOT NULL,
                                 `user_key` varchar(64) DEFAULT NULL,
                                 `issued_at` datetime DEFAULT NULL,
                                 `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                 `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                 `user_token` varchar(255) NOT NULL,
                                 `user_key_hash` varchar(50) DEFAULT NULL,
                                 PRIMARY KEY (`identity_id`),
                                 UNIQUE KEY `user_token` (`user_token`),
                                 UNIQUE KEY `user_key` (`user_key`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci