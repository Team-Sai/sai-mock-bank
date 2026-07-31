package org.teamsai.saimockbank.domain.test_identity.service;

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
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hashed = mac.doFinal(rawUserKey.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("해시 처리 실패", e);
        }
    }
}
