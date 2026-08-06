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
import org.teamsai.saimockbank.domain.user.dto.UserDTO;
import org.teamsai.saimockbank.domain.user.mapper.UserMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("dev")
@Transactional
public class BankAccountDTOMapperTest {

    @Autowired
    private BankAccountMapper bankAccountMapper;

    @Autowired
    private UserMapper userMapper;

    @Test
    void 사용자키로_계좌목록을_조회한다() {
        Long bankUserId = createBankUser("TEST_USER_001");
        createAccount(bankUserId, "김사이");

        List<BankAccountDTO> accounts = bankAccountMapper.findAllByUserKey("TEST_USER_001");

        assertThat(accounts).isNotEmpty();
        assertThat(accounts)
                .extracting(BankAccountDTO::getBankUserId)
                .containsOnly(bankUserId);
    }

    @Test
    void 계좌아이디로_계좌를_조회한다() {
        Long bankUserId = createBankUser("TEST_USER_002");
        Long accountId = createAccount(bankUserId, "김사이");

        BankAccountDTO account = bankAccountMapper.findById(accountId).orElseThrow();

        assertThat(account.getAccountHolderName()).isEqualTo("김사이");
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    private Long createBankUser(String userKeyHash) {
        UserDTO user = new UserDTO();
        LocalDateTime now = LocalDateTime.now();

        ReflectionTestUtils.setField(user, "name", "김사이");
        ReflectionTestUtils.setField(user, "email", "test-" + userKeyHash + "@example.com");  // 추가
        ReflectionTestUtils.setField(user, "password", "encoded-password");                    // 추가
        ReflectionTestUtils.setField(user, "userToken", "test-token-" + userKeyHash);
        ReflectionTestUtils.setField(user, "userKeyHash", userKeyHash);
        ReflectionTestUtils.setField(user, "issuedAt", now);
        ReflectionTestUtils.setField(user, "createdAt", now);
        ReflectionTestUtils.setField(user, "updatedAt", now);
        ReflectionTestUtils.setField(user, "birthDate", LocalDate.of(2003, 11, 18));
        userMapper.insert(user);
        return user.getBankUserId();
    }

    private Long createAccount(Long bankUserId, String holderName) {
        BankAccountDTO account = new BankAccountDTO();
        LocalDateTime now = LocalDateTime.now();

        ReflectionTestUtils.setField(account, "bankUserId", bankUserId);
        ReflectionTestUtils.setField(account, "bankCode", "088");
        ReflectionTestUtils.setField(account, "accountNumber", "ACC-" + bankUserId + "-" + System.nanoTime());  // 매번 고유하게
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