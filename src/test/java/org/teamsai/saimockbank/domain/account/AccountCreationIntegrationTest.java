package org.teamsai.saimockbank.domain.account;

import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.teamsai.saimockbank.domain.account.dto.CreateAccountRequest;
import org.teamsai.saimockbank.domain.account.exception.AccountErrorCode;
import org.teamsai.saimockbank.domain.account.mapper.BankAccountMapper;
import org.teamsai.saimockbank.domain.account.service.BankAccountService;
import org.teamsai.saimockbank.domain.account.util.AccountOwnershipValidator;
import org.teamsai.saimockbank.domain.user.mapper.UserMapper;
import org.teamsai.saimockbank.domain.user.service.UserKeyHasher;
import javax.sql.DataSource;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "SAI_BANK_DB_TEST", matches = "true")
@SpringJUnitConfig(AccountCreationIntegrationTest.Config.class)
@TestPropertySource(properties = "mock-bank.key-hash-secret=account-creation-test-only")
class AccountCreationIntegrationTest {
    @Test void customBankAndInitialBalancePersistAndDifferentReplayConflicts() {
        var key = UUID.randomUUID();
        var request = new CreateAccountRequest("CUSTOM", "생활비", "테스트은행", new java.math.BigDecimal("45678.90"));
        var created = service.postAccount(userId, key, request);
        assertThat(created.bankName()).isEqualTo("테스트은행");
        assertThat(created.balance()).isEqualByComparingTo("45678.90");
        assertThat(service.getMyAccounts(userId).get(0).bankName()).isEqualTo("테스트은행");
        assertThat(service.postAccount(userId, key, request).accountId()).isEqualTo(created.accountId());
        for (var changed : List.of(
                new CreateAccountRequest("CUSTOM", "생활비", "다른은행", new java.math.BigDecimal("45678.90")),
                new CreateAccountRequest("CUSTOM", "생활비", "테스트은행", new java.math.BigDecimal("45678.91")))) {
            assertThatThrownBy(() -> service.postAccount(userId, key, changed))
                    .extracting("errorCode").isEqualTo(AccountErrorCode.ACCOUNT_CREATION_CONFLICT);
        }
        assertThat(count()).isEqualTo(1);
    }

    @Configuration
    @EnableTransactionManagement
    @MapperScan(basePackageClasses = {UserMapper.class, BankAccountMapper.class})
    @Import({BankAccountService.class, AccountOwnershipValidator.class, UserKeyHasher.class})
    static class Config {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource(System.getenv("SAI_BANK_TEST_DB_URL"),
                    System.getenv("SAI_BANK_TEST_DB_USER"), System.getenv("SAI_BANK_TEST_DB_PASSWORD"));
        }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource ds) throws Exception {
            var factory = new SqlSessionFactoryBean();
            factory.setDataSource(ds);
            factory.setMapperLocations(new ClassPathResource("mapper/BankUserMapper.xml"),
                    new ClassPathResource("mapper/BankAccountMapper.xml"));
            return factory.getObject();
        }
        @Bean DataSourceTransactionManager transactionManager(DataSource ds) { return new DataSourceTransactionManager(ds); }
        @Bean JdbcTemplate jdbcTemplate(DataSource ds) { return new JdbcTemplate(ds); }
    }

    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;
    @Autowired BankAccountService service;
    @Autowired DataSourceTransactionManager transactionManager;
    Long userId;

    @BeforeEach void setup() {
        var schema = new ResourceDatabasePopulator(new ClassPathResource("db/bank_user.sql"),
                new ClassPathResource("db/bank_account.sql"));
        schema.setSqlScriptEncoding("UTF-8");
        schema.execute(dataSource);
        String token = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO bank_user(name,user_token,email,birth_date) VALUES ('계좌 테스트',?,?, '2000-01-01')",
                token, token + "@test.invalid");
        userId = jdbc.queryForObject("SELECT bank_user_id FROM bank_user WHERE user_token=?", Long.class, token);
    }

    @AfterEach void cleanup() {
        if (userId != null) {
            jdbc.update("DELETE FROM bank_account WHERE bank_user_id=?", userId);
            jdbc.update("DELETE FROM bank_user WHERE bank_user_id=?", userId);
        }
    }

    private long count() { return jdbc.queryForObject("SELECT COUNT(*) FROM bank_account WHERE bank_user_id=?", Long.class, userId); }

    @Test void concurrentSameRequestCreatesExactlyOneAccount() throws Exception {
        var pool = Executors.newFixedThreadPool(8);
        var start = new CountDownLatch(1);
        var key = UUID.randomUUID();
        try {
            List<Future<Long>> futures = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                futures.add(pool.submit(() -> {
                    if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("start timeout");
                    return service.postAccount(userId, key, CreateAccountRequest.defaults()).accountId();
                }));
            }
            start.countDown();
            Set<Long> ids = new HashSet<>();
            for (var future : futures) ids.add(future.get(20, TimeUnit.SECONDS));
            assertThat(ids).hasSize(1);
            assertThat(count()).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }

    @Test void changedConditionsConflictAndOriginalReplayStillWorks() {
        var key = UUID.randomUUID();
        var created = service.postAccount(userId, key, null);
        for (var changed : List.of(new CreateAccountRequest("004", "입출금통장"),
                new CreateAccountRequest("088", "저축"))) {
            assertThatThrownBy(() -> service.postAccount(userId, key, changed))
                    .extracting("errorCode").isEqualTo(AccountErrorCode.ACCOUNT_CREATION_CONFLICT);
        }
        assertThat(service.postAccount(userId, key, CreateAccountRequest.defaults()).accountId()).isEqualTo(created.accountId());
        assertThat(count()).isEqualTo(1);
    }

    @Test void newKeyCreatesAnotherAccountAndGeneratedFieldsAreLoaded() {
        var first = service.postAccount(userId, UUID.randomUUID(), null);
        var next = service.postAccount(userId, UUID.randomUUID(), new CreateAccountRequest("004", "생활비"));
        assertThat(first.accountId()).isNotEqualTo(next.accountId());
        assertThat(next.bankCode()).isEqualTo("004");
        assertThat(next.accountName()).isEqualTo("생활비");
        assertThat(next.balance()).isZero();
        assertThat(next.createdAt()).isNotNull();
        assertThat(count()).isEqualTo(2);
    }

    @Test void rollbackDoesNotConsumeKey() {
        var key = UUID.randomUUID();
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            service.postAccount(userId, key, null);
            tx.setRollbackOnly();
        });
        assertThat(count()).isZero();
        service.postAccount(userId, key, null);
        assertThat(count()).isEqualTo(1);
    }

    @Test void migrationCanRunAgainAndReplaysLegacyDefaultRequests() {
        var key = UUID.randomUUID();
        var created = service.postAccount(userId, key, null);
        jdbc.update("UPDATE bank_account SET creation_request_hash=NULL WHERE account_id=?", created.accountId());
        var schema = new ResourceDatabasePopulator(new ClassPathResource("db/bank_account.sql"));
        schema.setSqlScriptEncoding("UTF-8");
        schema.execute(dataSource);
        assertThat(service.postAccount(userId, key, null).accountId()).isEqualTo(created.accountId());
    }
}
