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

import java.time.LocalDateTime;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class BankLinkService {

    private final UserMapper userMapper;
    private final UserKeyHasher userKeyHasher;
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
    public MockBankLinkResponse issueUserKey(String name, String userToken, String operationId) {
        validateOperationId(operationId);
        UserDTO user = userMapper.findByNameAndUserToken(name, userToken)
                .orElseThrow(UserErrorCode.USER_NOT_FOUND::toException);
        user = userMapper.findByIdForUpdate(user.getBankUserId())
                .orElseThrow(UserErrorCode.USER_NOT_FOUND::toException);

        String rawKey = userKeyHasher.deriveUserKey(user.getBankUserId(), operationId);
        String hashedKey = userKeyHasher.hash(rawKey);
        var existing = userMapper.findKeyOperationForUpdate(user.getBankUserId(), operationId);
        if (existing.isPresent()) {
            var receipt = existing.get();
            if (receipt.recovered() || receipt.issuedAt() == null || !hashedKey.equals(receipt.keyHash())) {
                throw IdentityErrorCode.KEY_RECOVERY_CONFLICT.toException();
            }
            return new MockBankLinkResponse(rawKey, receipt.issuedAt());
        }
        LocalDateTime issuedAt = LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        LocalDateTime expiresAt = issuedAt.plusMinutes(PENDING_TTL_MINUTES);

        int updatedRow = userMapper.savePendingUserKey(
                user.getBankUserId(), hashedKey, issuedAt, expiresAt, operationId
        );
        if (updatedRow == 0) {
            throw IdentityErrorCode.PENDING_KEY_ALREADY_EXISTS.toException();
        }
        userMapper.insertKeyOperation(user.getBankUserId(), operationId, hashedKey, user.getUserKeyHash(), issuedAt);
        return new MockBankLinkResponse(rawKey, issuedAt);
    }

    @Transactional
    public void confirmUserKey(String rawUserKey, String operationId) {
        validateOperationId(operationId);
        String hashedKey = userKeyHasher.hash(rawUserKey);
        int updatedRow = userMapper.promotePendingToActive(hashedKey, operationId);
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

    /**
     * Pending cancellation is safe while the previous key remains active.
     * A confirmed key may be restored only to the bank-recorded predecessor,
     * within five minutes of confirmation. The grant is consumed on recovery.
     * An already restored state is a read-only success, even after expiry.
     * Expired/legacy active keys require reconciliation, never blind rollback.
     */
    @Transactional
    public void recoverUserKey(String userToken, String currentRawKey, String previousRawKey, String operationId) {
        validateOperationId(operationId);
        String currentHash = userKeyHasher.hash(currentRawKey);
        String previousHash = previousRawKey == null ? null : userKeyHasher.hash(previousRawKey);
        if (Objects.equals(currentHash, previousHash)) {
            throw IdentityErrorCode.KEY_RECOVERY_CONFLICT.toException();
        }

        var state = userMapper.findKeyRecoveryStateForUpdate(userToken)
                .orElseThrow(UserErrorCode.USER_NOT_FOUND::toException);
        var receipt = userMapper.findKeyOperationForUpdate(state.bankUserId(), operationId);
        if (receipt.isPresent()) {
            if (!currentHash.equals(receipt.get().keyHash())
                    || !Objects.equals(previousHash, receipt.get().previousKeyHash())) {
                throw IdentityErrorCode.KEY_RECOVERY_CONFLICT.toException();
            }
            // A completed A remains successful even if B is now pending or active.
            if (receipt.get().recovered()) return;
        }
        // Also bind read-only replays to the exact issuance; keys alone are insufficient.
        if (!operationId.equals(state.operationId()) || !currentHash.equals(state.rotationKeyHash())) {
            throw IdentityErrorCode.KEY_RECOVERY_CONFLICT.toException();
        }
        // confirm 후 보상 복구와 이미 복구된 요청의 재시도를 모두 허용합니다.
        boolean expectedActive = Objects.equals(state.activeKey(), currentHash)
                || Objects.equals(state.activeKey(), previousHash);
        boolean expectedPending = state.pendingKey() == null
                || Objects.equals(state.pendingKey(), currentHash);
        if (!expectedActive || !expectedPending) {
            throw IdentityErrorCode.KEY_RECOVERY_CONFLICT.toException();
        }

        if (Objects.equals(state.activeKey(), previousHash) && state.pendingKey() == null) {
            userMapper.saveRecoveryReceipt(state.bankUserId(), operationId, currentHash, previousHash);
            return; // Never rewrite the user's key status or timestamps.
        }
        if (Objects.equals(state.activeKey(), currentHash)
                && (!Objects.equals(state.recoveryPreviousKey(), previousHash)
                    || state.recoveryExpiresAt() == null)) {
            throw IdentityErrorCode.KEY_RECOVERY_CONFLICT.toException();
        }
        // Check the deadline in SQL after acquiring the row lock, using the same
        // DB clock as confirmation (independent of application timezone/skew).
        if (userMapper.recoverKeyState(state.bankUserId(), previousHash, operationId) != 1) {
            if (Objects.equals(state.activeKey(), currentHash)
                    && userMapper.isRecoveryExpired(state.bankUserId())) {
                throw IdentityErrorCode.KEY_RECOVERY_EXPIRED.toException();
            }
            throw IdentityErrorCode.KEY_RECOVERY_CONFLICT.toException();
        }
        userMapper.saveRecoveryReceipt(state.bankUserId(), operationId, currentHash, previousHash);
    }

    private static void validateOperationId(String operationId) {
        if (operationId == null
                || !operationId.matches("[A-Za-z0-9_-]{1,64}")) {
            throw IdentityErrorCode.INVALID_OPERATION_ID.toException();
        }
    }

}
