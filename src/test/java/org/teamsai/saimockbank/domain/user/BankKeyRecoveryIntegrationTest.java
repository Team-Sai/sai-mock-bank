package org.teamsai.saimockbank.domain.user;

import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.teamsai.saimockbank.domain.identity.exception.IdentityErrorCode;
import org.teamsai.saimockbank.domain.user.mapper.UserMapper;
import org.teamsai.saimockbank.domain.user.service.BankLinkService;
import org.teamsai.saimockbank.domain.user.service.UserKeyHasher;
import org.teamsai.saimockbank.global.exception.DomainException;

import javax.sql.DataSource;
import java.util.UUID;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "SAI_BANK_DB_TEST", matches = "true")
@SpringJUnitConfig(BankKeyRecoveryIntegrationTest.Config.class)
@TestPropertySource(properties = "mock-bank.key-hash-secret=recovery-integration-test-only")
class BankKeyRecoveryIntegrationTest {
    @Configuration
    @EnableTransactionManagement
    @MapperScan(basePackageClasses = UserMapper.class)
    @Import({BankLinkService.class, UserKeyHasher.class})
    static class Config {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource(System.getenv("SAI_BANK_TEST_DB_URL"),
                    System.getenv("SAI_BANK_TEST_DB_USER"), System.getenv("SAI_BANK_TEST_DB_PASSWORD"));
        }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
            var bean = new SqlSessionFactoryBean();
            bean.setDataSource(dataSource);
            bean.setMapperLocations(new ClassPathResource("mapper/BankUserMapper.xml"));
            return bean.getObject();
        }
        @Bean DataSourceTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
        @Bean JdbcTemplate jdbcTemplate(DataSource dataSource) { return new JdbcTemplate(dataSource); }
    }

    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;
    @Autowired BankLinkService service;
    @Autowired UserKeyHasher hasher;
    @Autowired UserMapper mapper;
    @Autowired DataSourceTransactionManager transactionManager;
    String token;

    @BeforeEach void setup() {
        new ResourceDatabasePopulator(new ClassPathResource("db/bank_user.sql")).execute(dataSource);
        token = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO bank_user(name, user_token, email, birth_date)
                VALUES ('Recovery Test', ?, ?, '2000-01-01')
                """, token, token + "@test.invalid");
    }

    @AfterEach void cleanup() { jdbc.update("DELETE FROM bank_user WHERE user_token=?", token); }

    private String hash(String key) { return key == null ? null : hasher.hash(key); }

    private void state(String active, String pending) {
        jdbc.update("""
                UPDATE bank_user SET user_key_hash=?, pending_user_key=?, key_status=?,
                pending_issued_at=NOW(), pending_expires_at=DATE_ADD(NOW(), INTERVAL 5 MINUTE)
                WHERE user_token=?
                """, hash(active), hash(pending), pending == null ? "ACTIVE" : "PENDING", token);
    }

    private void assertRecovered(String previous) {
        var row = jdbc.queryForMap("SELECT * FROM bank_user WHERE user_token=?", token);
        assertThat(row.get("user_key_hash")).isEqualTo(hash(previous));
        assertThat(row.get("recovery_previous_key")).isNull();
        assertThat(row.get("recovery_expires_at")).isNull();
        assertThat(row.get("pending_user_key")).isNull();
        assertThat(row.get("pending_issued_at")).isNull();
        assertThat(row.get("pending_expires_at")).isNull();
        assertThat(row.get("key_status")).isEqualTo(previous == null ? "EXPIRED" : "ACTIVE");
    }

    @ParameterizedTest
    @CsvSource(value = {"NULL,new,NULL",
            "old,new,old", "old,NULL,old"}, nullValues = "NULL")
    void recoversAndRepeatedRequestIsSafe(String active, String pending, String previous) {
        state(active, pending);
        if (pending == null) {
            jdbc.update("UPDATE bank_user SET pending_issued_at=NULL, pending_expires_at=NULL WHERE user_token=?", token);
        }
        service.recoverUserKey(token, "new", previous);
        var recovered = jdbc.queryForMap("SELECT * FROM bank_user WHERE user_token=?", token);
        service.recoverUserKey(token, "new", previous);
        assertThat(jdbc.queryForMap("SELECT * FROM bank_user WHERE user_token=?", token)).isEqualTo(recovered);
        assertRecovered(previous);
    }

    @ParameterizedTest
    @CsvSource(value = {"other,NULL", "old,other", "new,other"}, nullValues = "NULL")
    void conflictsLeaveBankStateUntouched(String active, String pending) {
        state(active, pending);
        var before = jdbc.queryForMap("SELECT * FROM bank_user WHERE user_token=?", token);
        assertThatThrownBy(() -> service.recoverUserKey(token, "new", "old"))
                .extracting("errorCode").isEqualTo(IdentityErrorCode.KEY_RECOVERY_CONFLICT);
        assertThat(jdbc.queryForMap("SELECT * FROM bank_user WHERE user_token=?", token)).isEqualTo(before);
    }

    @Test void pendingKeyCannotBeConfirmedAfterRecovery() {
        state("old", "new");
        service.recoverUserKey(token, "new", "old");
        assertThatThrownBy(() -> service.confirmUserKey("new"))
                .extracting("errorCode").isEqualTo(IdentityErrorCode.PENDING_KEY_NOT_FOUND);
        assertRecovered("old");
    }

    @Test void confirmationCompletedBeforeRecoveryIsRolledBack() {
        state("old", "new");
        service.confirmUserKey("new");
        service.recoverUserKey(token, "new", "old");
        assertRecovered("old");
    }

    @Test void expiredConfirmationCannotBeRolledBack() {
        state("old", "new");
        service.confirmUserKey("new");
        jdbc.update("UPDATE bank_user SET recovery_expires_at=NOW(6) WHERE user_token=?", token);
        var before = jdbc.queryForMap("SELECT * FROM bank_user WHERE user_token=?", token);
        assertThatThrownBy(() -> service.recoverUserKey(token, "new", "old"))
                .extracting("errorCode").isEqualTo(IdentityErrorCode.KEY_RECOVERY_CONFLICT);
        assertThat(jdbc.queryForMap("SELECT * FROM bank_user WHERE user_token=?", token)).isEqualTo(before);
    }

    @Test void sqlDeadlineGuardRejectsExpiredGrantWithoutChangingState() {
        state("old", "new");
        service.confirmUserKey("new");
        jdbc.update("UPDATE bank_user SET recovery_expires_at=NOW(6) WHERE user_token=?", token);
        var before = jdbc.queryForMap("SELECT * FROM bank_user WHERE user_token=?", token);
        var tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            var locked = mapper.findKeyRecoveryStateForUpdate(token).orElseThrow();
            assertThat(mapper.recoverKeyState(locked.bankUserId(), hash("old"))).isZero();
        });
        assertThat(jdbc.queryForMap("SELECT * FROM bank_user WHERE user_token=?", token)).isEqualTo(before);
    }

    @Test void expiredPendingCanStillBeSafelyCancelled() {
        state("old", "new");
        jdbc.update("UPDATE bank_user SET pending_expires_at=NOW() WHERE user_token=?", token);
        service.recoverUserKey(token, "new", "old");
        assertRecovered("old");
    }
    @Test void legacyConfirmedKeyWithoutGrantCannotBeRolledBack() {
        state("new", null);
        assertThatThrownBy(() -> service.recoverUserKey(token, "new", "old"))
                .extracting("errorCode").isEqualTo(IdentityErrorCode.KEY_RECOVERY_CONFLICT);
    }

    @Test void confirmationRecordsActualPredecessorAndDoesNotRenewOnReplay() {
        state("old", "new");
        service.confirmUserKey("new");
        var before = jdbc.queryForMap("SELECT * FROM bank_user WHERE user_token=?", token);
        assertThat(before.get("recovery_previous_key")).isEqualTo(hash("old"));
        assertThat(before.get("recovery_expires_at")).isNotNull();
        assertThatThrownBy(() -> service.confirmUserKey("new"))
                .extracting("errorCode").isEqualTo(IdentityErrorCode.PENDING_KEY_NOT_FOUND);
        assertThatThrownBy(() -> service.recoverUserKey(token, "new", "different"))
                .extracting("errorCode").isEqualTo(IdentityErrorCode.KEY_RECOVERY_CONFLICT);
        assertThat(jdbc.queryForMap("SELECT * FROM bank_user WHERE user_token=?", token)).isEqualTo(before);
    }

    @Test void firstConfirmedKeyCanBeRecoveredAndReplayedWithoutWrites() {
        state(null, "new");
        service.confirmUserKey("new");
        service.recoverUserKey(token, "new", null);
        assertRecovered(null);
        var before = jdbc.queryForMap("SELECT * FROM bank_user WHERE user_token=?", token);
        service.recoverUserKey(token, "new", null);
        assertThat(jdbc.queryForMap("SELECT * FROM bank_user WHERE user_token=?", token)).isEqualTo(before);
    }

    @Test void replayCannotUndoSubsequentRotation() {
        state("old", "new");
        service.confirmUserKey("new");
        service.recoverUserKey(token, "new", "old");
        state("old", "next");
        service.confirmUserKey("next");
        assertThatThrownBy(() -> service.recoverUserKey(token, "new", "old"))
                .extracting("errorCode").isEqualTo(IdentityErrorCode.KEY_RECOVERY_CONFLICT);
        assertThat(jdbc.queryForMap("SELECT * FROM bank_user WHERE user_token=?", token).get("user_key_hash"))
                .isEqualTo(hash("next"));
    }
    @Test void concurrentConfirmAndRecoveryAlwaysEndWithPreviousKey() throws Exception {
        state("old", "new");
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var confirm = pool.submit(() -> {
                await(start);
                try { service.confirmUserKey("new"); }
                catch (DomainException e) {
                    assertThat(e.getErrorCode()).isEqualTo(IdentityErrorCode.PENDING_KEY_NOT_FOUND);
                }
            });
            var recover = pool.submit(() -> { await(start); service.recoverUserKey(token, "new", "old"); });
            start.countDown();
            confirm.get(10, TimeUnit.SECONDS);
            recover.get(10, TimeUnit.SECONDS);
            assertRecovered("old");
        } finally { pool.shutdownNow(); }
    }

    @Test void recoveryWaitsForUserRowLockHeldByAnotherTransaction() throws Exception {
        state("old", "new");
        var pool = Executors.newSingleThreadExecutor();
        var started = new CountDownLatch(1);
        var transaction = new TransactionTemplate(transactionManager);
        try {
            Future<?> recovery = transaction.execute(status -> {
                mapper.findKeyRecoveryStateForUpdate(token).orElseThrow();
                var pending = pool.submit(() -> { started.countDown(); service.recoverUserKey(token, "new", "old"); });
                await(started);
                assertThatThrownBy(() -> pending.get(250, TimeUnit.MILLISECONDS))
                        .isInstanceOf(TimeoutException.class);
                return pending;
            });
            recovery.get(10, TimeUnit.SECONDS);
            assertRecovered("old");
        } finally { pool.shutdownNow(); }
    }

    private static void await(CountDownLatch latch) {
        try { if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("Timed out"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
    }
}
