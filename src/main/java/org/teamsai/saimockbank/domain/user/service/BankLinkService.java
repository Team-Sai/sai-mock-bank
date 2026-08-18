package org.teamsai.saimockbank.domain.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saimockbank.domain.identity.exception.IdentityErrorCode;
import org.teamsai.saimockbank.domain.user.dto.MockBankLinkResponse;
import org.teamsai.saimockbank.domain.user.dto.UserDTO;
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
    private static final long PENDING_TTL_MINUTES = 5;

    /**
     * 새 userKey를 발급하되, 즉시 활성화하지 않고 PENDING 상태로만 저장합니다.
     *
     * 주의: key_status가 'PENDING'으로 바뀌어도 기존 user_key_hash(구 키)는
     * 그대로 남아 계속 유효합니다. 실제 계좌 접근 권한 검사는 key_status가 아니라
     * user_key_hash IS NOT NULL 여부로만 이루어지므로, key_status는 "재발급
     * 워크플로우의 진행 상태"를 나타낼 뿐 "현재 인증 가능 여부"를 나타내지 않습니다.
     * 이는 콜백 유실 시에도 서비스가 끊기지 않도록 하기 위한 의도된 설계입니다.
     */
    @Transactional
    public MockBankLinkResponse issueUserKey(String name, String userToken) {
        UserDTO user = userMapper.findByNameAndUserToken(name, userToken)
                .orElseThrow(UserErrorCode.USER_NOT_FOUND::toException);

        String rawKey = generateUserKey();
        String hashedKey = userKeyHasher.hash(rawKey);
        LocalDateTime issuedAt = LocalDateTime.now();
        LocalDateTime expiresAt = issuedAt.plusMinutes(PENDING_TTL_MINUTES);

        int updatedRow = userMapper.savePendingUserKey(
                user.getBankUserId(), hashedKey, issuedAt, expiresAt
        );
        if (updatedRow == 0) {
            throw IdentityErrorCode.PENDING_KEY_ALREADY_EXISTS.toException();
        }
        return new MockBankLinkResponse(rawKey, issuedAt);
    }

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
    }

    @Transactional
    public void revokeUserKey(String rawUserKey) {
        String hashedKey = userKeyHasher.hash(rawUserKey);
        int updatedRow = userMapper.revokeUserKey(hashedKey);
        if (updatedRow == 0) {
            throw IdentityErrorCode.ACTIVE_KEY_NOT_FOUND.toException();
        }
    }

    @Transactional
    public void restoreUserKey(String currentRawKey, String previousRawKey) {
        String currentHashed = userKeyHasher.hash(currentRawKey);
        String previousHashed = userKeyHasher.hash(previousRawKey);
        int updatedRow = userMapper.restoreActiveKey(currentHashed, previousHashed);
        if (updatedRow == 0) {
            throw IdentityErrorCode.ACTIVE_KEY_NOT_FOUND.toException();
        }
    }
    
    private String generateUserKey() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return "mb_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
