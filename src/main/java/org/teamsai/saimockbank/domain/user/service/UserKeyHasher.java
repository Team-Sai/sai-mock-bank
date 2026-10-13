package org.teamsai.saimockbank.domain.user.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

@Component
public class UserKeyHasher {

    @Value("${mock-bank.key-hash-secret}")
    private String secret;

    public String hash(String rawUserKey) {
        return hmac(rawUserKey);
    }

    // Domain-separated deterministic issuance: retries need no plaintext key in the DB.
    public String deriveUserKey(Long bankUserId, String operationId) {
        return "mb_" + hmac("bank-key-issuance:v1\n" + bankUserId + "\n" + operationId);
    }

    private String hmac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hashed = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("해시 처리 실패", e);
        }
    }
}
