package org.teamsai.saimockbank.domain.account;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saimockbank.domain.account.dto.AccountStatus;
import org.teamsai.saimockbank.domain.account.dto.BankAccountDTO;
import org.teamsai.saimockbank.domain.account.mapper.BankAccountMapper;
import org.teamsai.saimockbank.domain.identity.dto.IdentityDTO;
import org.teamsai.saimockbank.domain.identity.mapper.IdentityMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

@SpringBootTest
@ActiveProfiles("dev")
@Transactional
public class BankAccountMapperTest {

    @Autowired
    private BankAccountMapper bankAccountMapper;

    @Autowired
    private IdentityMapper identityMapper;

    @Test
    void 사용자키로_계좌목록을_조회한다() {
        Long identityId = createIdentity("TEST_USER_001");
        createAccount(identityId, "김사이");

        List<BankAccountDTO> accounts = bankAccountMapper.findAllByUserKey("TEST_USER_001");

        assertThat(accounts).isNotEmpty();
        assertThat(accounts)
                .extracting(BankAccountDTO::getBankIdentityId)
                .containsOnly(identityId);
    }

    @Test
    void 계좌아이디로_계좌를_조회한다() {
        Long identityId = createIdentity("TEST_USER_002");
        Long accountId = createAccount(identityId, "김사이");

        BankAccountDTO account = bankAccountMapper.findById(accountId).orElseThrow();

        assertThat(account.getAccountHolderName()).isEqualTo("김사이");
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    private Long createIdentity(String userKeyHash) {
        IdentityDTO identity = new IdentityDTO();
        LocalDateTime now = LocalDateTime.now();

        ReflectionTestUtils.setField(identity, "name", "김사이");
        ReflectionTestUtils.setField(identity, "userToken", "test-token-" + userKeyHash);
        ReflectionTestUtils.setField(identity, "userKeyHash", userKeyHash);
        ReflectionTestUtils.setField(identity, "issuedAt", now);
        ReflectionTestUtils.setField(identity, "createdAt", now);
        ReflectionTestUtils.setField(identity, "updatedAt", now);

        identityMapper.insert(identity);
        return identity.getBankIdentityId();
    }

    private Long createAccount(Long identityId, String holderName) {
        BankAccountDTO account = new BankAccountDTO();
        LocalDateTime now = LocalDateTime.now();

        ReflectionTestUtils.setField(account, "bankIdentityId", identityId);
        ReflectionTestUtils.setField(account, "bankCode", "088");
        ReflectionTestUtils.setField(account, "accountNumber", "1234567890");
        ReflectionTestUtils.setField(account, "accountName", "테스트계좌");
        ReflectionTestUtils.setField(account, "accountHolderName", holderName);
        ReflectionTestUtils.setField(account, "balance", BigDecimal.valueOf(100000));
        ReflectionTestUtils.setField(account, "status", AccountStatus.ACTIVE);
        ReflectionTestUtils.setField(account, "createdAt", now);
        ReflectionTestUtils.setField(account, "updatedAt", now);

        bankAccountMapper.insert(account);
        return account.getAccountId();
    }
}