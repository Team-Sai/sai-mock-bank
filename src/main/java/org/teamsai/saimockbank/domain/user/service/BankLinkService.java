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
import org.teamsai.saimockbank.domain.user.type.UserKeyStatus;

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
                user.getBankUserId(), hashedKey, issuedAt, expiresAt,
                UserKeyStatus.PENDING.name()
        );
        if (updatedRow == 0) {
            throw IdentityErrorCode.PENDING_KEY_ALREADY_EXISTS.toException();
        }

        return new MockBankLinkResponse(rawKey, issuedAt);
    }

    private static final long PENDING_TTL_MINUTES = 5;

    @Transactional
    public void confirmUserKey(String rawUserKey) {
        String hashedKey = userKeyHasher.hash(rawUserKey);
        int updatedRow = userMapper.promotePendingToActive(
                hashedKey,
                UserKeyStatus.ACTIVE.name(),
                UserKeyStatus.PENDING.name()
        );
        if (updatedRow == 0) {
            throw IdentityErrorCode.PENDING_KEY_NOT_FOUND.toException();
        }
    }
    /**
     * 만료된 PENDING 상태를 EXPIRED로 정리합니다.
     * TODO(#139): 현재 이 메서드를 호출하는 배치/스케줄러가 없습니다.
     *   confirm 안 된 PENDING은 promotePendingToActive/savePendingUserKey의
     *   만료 조건으로 기능상 우회되지만, key_status='PENDING' 값 자체는
     *   DB에 영구히 남습니다. 별도 이슈에서 @Scheduled 배치로 정리 예정.
     */
    @Transactional
    public void expireUserKey(Long bankUserId, LocalDateTime pendingIssuedAt) {
        userMapper.markPendingExpired(
                bankUserId, pendingIssuedAt,
                UserKeyStatus.EXPIRED.name(),
                UserKeyStatus.PENDING.name()
        );
    }

    private String generateUserKey() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return "mb_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
