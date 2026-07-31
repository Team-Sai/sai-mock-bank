package org.teamsai.saimockbank.domain.test_identity.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.teamsai.saimockbank.domain.test_identity.dto.MockBankLinkResponse;
import org.teamsai.saimockbank.domain.test_identity.entity.TestIdentity;
import org.teamsai.saimockbank.domain.test_identity.exception.AlreadyLinkedException;
import org.teamsai.saimockbank.domain.test_identity.exception.IdentityNotFoundException;
import org.teamsai.saimockbank.domain.test_identity.mapper.TestIdentityMapper;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

import static org.teamsai.saimockbank.domain.test_identity.exception.ErrorCode.ALREADY_LINKED_USER;
import static org.teamsai.saimockbank.domain.test_identity.exception.ErrorCode.CORRECT_USER_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class BankLinkService {

    private final TestIdentityMapper testIdentityMapper;
    private final UserKeyHasher userKeyHasher;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public MockBankLinkResponse issueUserKey(String name, String email) {
        TestIdentity identity = testIdentityMapper.findByNameAndEmail(name, email);

        if (identity.getUserKey() != null) {
            // 이미 발급된 원본 키는 다시 복원할 수 없으므로(단방향), 응답으로 재발급은 불가
            throw new AlreadyLinkedException(ALREADY_LINKED_USER);
        }
        if (identity == null) {
            throw new IdentityNotFoundException(CORRECT_USER_NOT_FOUND);
        }

        String rawKey = generateUserKey();
        String hashedKey = userKeyHasher.hash(rawKey);
        LocalDateTime now = LocalDateTime.now();
        testIdentityMapper.updateUserKey(identity.getIdentityId(), hashedKey, LocalDateTime.now());

        return new MockBankLinkResponse(rawKey, LocalDateTime.now());
    }

    private String generateUserKey() { //유니크 충돌 처리 나중에 해야함.
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return "mb_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
