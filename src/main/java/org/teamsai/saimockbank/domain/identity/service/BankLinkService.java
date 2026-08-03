package org.teamsai.saimockbank.domain.identity.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saimockbank.domain.identity.dto.IdentityDTO;
import org.teamsai.saimockbank.domain.identity.dto.MockBankLinkResponse;
import org.teamsai.saimockbank.domain.identity.exception.IdentityErrorCode;
import org.teamsai.saimockbank.domain.identity.mapper.IdentityMapper;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
public class BankLinkService {

    private final IdentityMapper identityMapper;
    private final UserKeyHasher userKeyHasher;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Transactional
    public MockBankLinkResponse issueUserKey(String name, String userToken) {
        //log.info("[issueUserKey] 요청 수신 -> name: '{}', userToken: '{}'", name, userToken);

        IdentityDTO identity = identityMapper.findByNameAndUserToken(name, userToken)
                .orElseThrow(IdentityErrorCode.CORRECT_USER_NOT_FOUND::toException);

        //log.info("[issueUserKey] 회원 조회 성공 -> identityId: {}, userKey 존재여부: {}",identity.getIdentityId(), identity.getUserKey() != null);

        if (identity.getUserKeyHash() != null) {
            throw IdentityErrorCode.CONFLICT.toException();
        }

        String rawKey = generateUserKey();
        String hashedKey = userKeyHasher.hash(rawKey);
        LocalDateTime issuedAt = LocalDateTime.now();

        int updatedRow = identityMapper.updateUserKey(identity.getIdentityId(), hashedKey, issuedAt);

        if(updatedRow==0){
            throw IdentityErrorCode.CONFLICT.toException();
        }
        return new MockBankLinkResponse(rawKey, issuedAt);
    }

    private String generateUserKey() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return "mb_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
