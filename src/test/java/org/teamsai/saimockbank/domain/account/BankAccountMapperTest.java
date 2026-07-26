package org.teamsai.saimockbank.domain.account;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.teamsai.saimockbank.domain.account.entity.AccountStatus;
import org.teamsai.saimockbank.domain.account.entity.BankAccount;
import org.teamsai.saimockbank.domain.account.mapper.BankAccountMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("dev")
public class BankAccountMapperTest {

    @Autowired
    private BankAccountMapper bankAccountMapper;

    @Test
    void 사용자키로_계좌목록을_조회한다(){
        List<BankAccount> accounts = bankAccountMapper.findAllByUserKey("TEST_USER_001");

        assertThat(accounts).isNotEmpty();
        assertThat(accounts)
                .extracting(BankAccount::getUserKey)
                .containsOnly("TEST_USER_001");
    }

    @Test
    void 계좌아이디로_계좌를_조회한다(){
        BankAccount account = bankAccountMapper.findById(1L).orElseThrow();

        assertThat(account.getOwnerName()).isEqualTo("김사이");
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }
}
