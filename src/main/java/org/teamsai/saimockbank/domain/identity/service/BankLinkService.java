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

        IdentityDTO identity = identityMapper.findByNameAndUserToken(name, userToken)
                .orElseThrow(IdentityErrorCode.CORRECT_USER_NOT_FOUND::toException);


        if (identity.getUserKeyHash() != null) {
            throw IdentityErrorCode.ALREADY_LINKED_USER.toException();
        }

        String rawKey = generateUserKey();
        String hashedKey = userKeyHasher.hash(rawKey);
        LocalDateTime issuedAt = LocalDateTime.now();

        int updatedRow = identityMapper.updateUserKey(identity.getBankIdentityId(), hashedKey, issuedAt);

        if(updatedRow==0){
            throw IdentityErrorCode.ALREADY_LINKED_USER.toException();
        }
        return new MockBankLinkResponse(rawKey, issuedAt);
    }

    private String generateUserKey() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return "mb_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
