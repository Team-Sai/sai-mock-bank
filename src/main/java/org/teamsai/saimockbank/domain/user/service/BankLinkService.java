package org.teamsai.saimockbank.domain.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saimockbank.domain.identity.exception.IdentityErrorCode;
import org.teamsai.saimockbank.domain.user.dto.UserDTO;
import org.teamsai.saimockbank.domain.user.dto.MockBankLinkResponse;
import org.teamsai.saimockbank.domain.user.exception.UserErrorCode;
import org.teamsai.saimockbank.domain.user.mapper.UserMapper;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
public class BankLinkService {

    private final UserMapper userMapper;
    private final UserKeyHasher userKeyHasher;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Transactional
    public MockBankLinkResponse issueUserKey(String name, String userToken) {
        UserDTO user = userMapper.findByNameAndUserToken(name, userToken)
                .orElseThrow(UserErrorCode.USER_NOT_FOUND::toException);

        String rawKey = generateUserKey();
        String hashedKey = userKeyHasher.hash(rawKey);
        LocalDateTime issuedAt = LocalDateTime.now();
        LocalDateTime expiresAt = issuedAt.plusMinutes(PENDING_TTL_MINUTES);

        int updatedRow = userMapper.savePendingUserKey(
                user.getBankUserId(), hashedKey, issuedAt, expiresAt);
        if (updatedRow == 0) {
            throw IdentityErrorCode.PENDING_KEY_ALREADY_EXISTS.toException();
        }

        return new MockBankLinkResponse(rawKey, issuedAt);
    }

    private static final long PENDING_TTL_MINUTES = 5;

    @Transactional
    public void confirmUserKey(String rawUserKey) {
        String hashedKey = userKeyHasher.hash(rawUserKey);
        int updatedRow = userMapper.promotePendingToActive(hashedKey);
        if (updatedRow == 0) {
            throw IdentityErrorCode.PENDING_KEY_NOT_FOUND.toException();
        }
    }

    @Transactional
    public void expireUserKey(Long bankUserId, LocalDateTime pendingIssuedAt) {
        userMapper.markPendingExpired(bankUserId, pendingIssuedAt);
        // updatedRow == 0이어도 무시: 이미 confirm됐거나 배치가 먼저 처리한 정상 상황
    }

    private String generateUserKey() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return "mb_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
