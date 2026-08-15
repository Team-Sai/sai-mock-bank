package org.teamsai.saimockbank.global.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Optional;

@Component
public class JwtTokenProvider {

    private static final String CLAIM_PURPOSE = "purpose";
    private static final String PURPOSE_ACCESS = "access";
    private static final String PURPOSE_BANK_LINK = "bank-link";

    private final SecretKey accessSigningKey;
    private final SecretKey linkStateSigningKey;
    private final long accessTokenExpirationMs;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String accessSecret,
            @Value("${link-state.secret}") String linkStateSecret,
            @Value("${jwt.access-token-expiration-ms}")
            long accessTokenExpirationMs
    ) {
        this.accessSigningKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(accessSecret));
        this.linkStateSigningKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(linkStateSecret));
        this.accessTokenExpirationMs = accessTokenExpirationMs;
    }

    public String createAccessToken(Long userId) {
        Date issuedAt = new Date();
        Date expiration = new Date(
                issuedAt.getTime() + accessTokenExpirationMs
        );
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_PURPOSE, PURPOSE_ACCESS)
                .issuedAt(issuedAt)
                .expiration(expiration)
                .signWith(accessSigningKey)
                .compact();
    }

    public Optional<Long> getUserIdIfValid(String token) {
        try {
            Claims claims = parseClaims(token, accessSigningKey);
            if (!PURPOSE_ACCESS.equals(claims.get(CLAIM_PURPOSE, String.class))) {
                return Optional.empty();
            }
            String subject = claims.getSubject();
            if (subject == null || subject.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(Long.valueOf(subject));
        } catch (JwtException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public Optional<String> getIdentityHashFromLinkState(String token) {
        try {
            Claims claims = parseClaims(token, linkStateSigningKey);
            if (!PURPOSE_BANK_LINK.equals(claims.get(CLAIM_PURPOSE, String.class))) {
                return Optional.empty();
            }
            String identityHash = claims.get("identity-hash", String.class);
            return Optional.ofNullable(identityHash);
        } catch (JwtException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private Claims parseClaims(String token, SecretKey key) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}