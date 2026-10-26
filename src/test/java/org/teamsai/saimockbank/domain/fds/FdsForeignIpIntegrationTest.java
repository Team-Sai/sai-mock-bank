
package org.teamsai.saimockbank.domain.fds;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.context.ActiveProfiles;
import org.teamsai.saimockbank.domain.transfer.dto.TransferRequest;
import org.teamsai.saimockbank.domain.transfer.dto.TransferResponse;
import org.teamsai.saimockbank.domain.transfer.dto.TransferStatus;
import org.teamsai.saimockbank.domain.transfer.service.BankTransferService;

import java.math.BigDecimal;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("dev")
class FdsForeignIpIntegrationTest {

    @Autowired
    private BankTransferService transferService;

    @Autowired
    private JdbcTemplate jdbc;

    private final List<Long> createdUsers = new ArrayList<>();
    private final List<Long> createdAccounts = new ArrayList<>();
    private final List<String> requestKeys = new ArrayList<>();

    private record Scenario(
            Long userId,
            Long senderAccountId,
            Long receiverAccountId,
            String receiverAccountNumber,
            String requestKey
    ) {
    }

    @Test
    void 국내_IP_송금은_정상_처리된다() {
        Scenario scenario = prepareScenario();

        TransferResponse response = executeTransfer(
                scenario,
                "127.0.0.1"
        );

        assertThat(response.status())
                .isEqualTo(TransferStatus.SUCCESS);

        assertThat(evaluationScore(response.transferId()))
                .isZero();

        assertThat(evaluationLevel(response.transferId()))
                .isEqualTo("LOW");

        assertThat(evaluationAction(response.transferId()))
                .isEqualTo("ALLOW");

        assertThat(matchedRules(response.transferId()))
                .doesNotContain("R04");

        assertThat(transactionCount(response.transferId()))
                .isEqualTo(2);

        System.out.println(
                "[PASS] 국내 IP → LOW / SUCCESS"
        );
    }

    @Test
    void 미국_IP_송금은_R04로_보류된다() {
        verifyForeignIp("203.0.113.10", "US");
    }

    @Test
    void 일본_IP_송금은_R04로_보류된다() {
        verifyForeignIp("198.51.100.20", "JP");
    }

    @Test
    void 국가_판별_불가_IP는_R04로_탐지하지_않는다() {
        Scenario scenario = prepareScenario();

        TransferResponse response = executeTransfer(
                scenario,
                "192.0.2.55"
        );

        assertThat(response.status())
                .isEqualTo(TransferStatus.SUCCESS);

        assertThat(matchedRules(response.transferId()))
                .doesNotContain("R04");

        assertThat(evaluationScore(response.transferId()))
                .isZero();

        System.out.println(
                "[PASS] UNKNOWN IP → R04 미탐지"
        );
    }

    private void verifyForeignIp(
            String clientIp,
            String countryCode
    ) {
        Scenario scenario = prepareScenario();

        BigDecimal senderBefore =
                balance(scenario.senderAccountId());

        BigDecimal receiverBefore =
                balance(scenario.receiverAccountId());

        TransferResponse response = executeTransfer(
                scenario,
                clientIp
        );

        assertThat(response.status())
                .isEqualTo(TransferStatus.PENDING_REVIEW);

        assertThat(response.completedAt())
                .isNull();

        assertThat(evaluationScore(response.transferId()))
                .isEqualTo(90);

        assertThat(evaluationLevel(response.transferId()))
                .isEqualTo("HIGH");

        assertThat(evaluationAction(response.transferId()))
                .isEqualTo("HOLD");

        assertThat(matchedRules(response.transferId()))
                .contains("R04");

        assertThat(evaluationReasons(response.transferId()))
                .contains(countryCode);

        assertThat(transactionCount(response.transferId()))
                .isZero();

        assertThat(balance(scenario.senderAccountId()))
                .isEqualByComparingTo(senderBefore);

        assertThat(balance(scenario.receiverAccountId()))
                .isEqualByComparingTo(receiverBefore);

        System.out.println(
                "[PASS] " + countryCode
                        + " IP → R04 / HIGH 90 / PENDING_REVIEW"
        );
    }

    private Scenario prepareScenario() {
        Long senderUserId = createUser();
        Long receiverUserId = createUser();

        String senderNumber = accountNumber();
        String receiverNumber = accountNumber();

        Long senderAccountId = createAccount(
                senderUserId,
                senderNumber
        );

        Long receiverAccountId = createAccount(
                receiverUserId,
                receiverNumber
        );

        String requestKey = "fds-R04-" + UUID.randomUUID();

        requestKeys.add(requestKey);

        return new Scenario(
                senderUserId,
                senderAccountId,
                receiverAccountId,
                receiverNumber,
                requestKey
        );
    }

    private TransferResponse executeTransfer(
            Scenario scenario,
            String clientIp
    ) {
        TransferRequest request = new TransferRequest(
                scenario.requestKey(),
                scenario.senderAccountId(),
                scenario.receiverAccountNumber(),
                BigDecimal.valueOf(10_000),
                "R04 테스트",
                "R04 테스트"
        );

        return transferService.transfer(
                scenario.userId(),
                request,
                clientIp
        );
    }

    private Long createUser() {
        String unique = UUID.randomUUID()
                .toString()
                .replace("-", "");

        Long id = insertAndGetId(
                """
                INSERT INTO bank_user (
                    name,
                    email,
                    password,
                    user_token,
                    birth_date,
                    role
                ) VALUES (?, ?, ?, ?, ?, ?)
                """,
                "FDS R04 TEST",
                "R04-" + unique + "@test.local",
                "test-only",
                "R04-" + unique,
                java.sql.Date.valueOf("2000-01-01"),
                "USER"
        );

        createdUsers.add(id);
        return id;
    }

    private Long createAccount(
            Long userId,
            String number
    ) {
        Long id = insertAndGetId(
                """
                INSERT INTO bank_account (
                    bank_user_id,
                    bank_code,
                    account_number,
                    account_name,
                    account_holder_name,
                    balance,
                    status
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                userId,
                "088",
                number,
                "R04 테스트 계좌",
                "FDS 테스트",
                BigDecimal.valueOf(1_000_000),
                "ACTIVE"
        );

        createdAccounts.add(id);
        return id;
    }

    private String accountNumber() {
        return "R04" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 16);
    }

    private Long insertAndGetId(
            String sql,
            Object... parameters
    ) {
        KeyHolder holder = new GeneratedKeyHolder();

        jdbc.update(connection -> {
            var statement = connection.prepareStatement(
                    sql,
                    Statement.RETURN_GENERATED_KEYS
            );

            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }

            return statement;
        }, holder);

        Number key = holder.getKey();

        assertThat(key).isNotNull();

        return key.longValue();
    }

    private BigDecimal balance(Long accountId) {
        return jdbc.queryForObject(
                "SELECT balance FROM bank_account WHERE account_id = ?",
                BigDecimal.class,
                accountId
        );
    }

    private long transactionCount(Long transferId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM bank_transaction WHERE transfer_id = ?",
                Long.class,
                transferId
        );

        return count == null ? 0 : count;
    }

    private int evaluationScore(Long transferId) {
        Integer score = jdbc.queryForObject(
                """
                SELECT risk_score
                FROM fds_evaluation
                WHERE transaction_id = ?
                ORDER BY id DESC
                LIMIT 1
                """,
                Integer.class,
                transferId
        );

        assertThat(score).isNotNull();
        return score;
    }

    private String evaluationLevel(Long transferId) {
        return evaluationValue("risk_level", transferId);
    }

    private String evaluationAction(Long transferId) {
        return evaluationValue("action", transferId);
    }

    private String matchedRules(Long transferId) {
        return evaluationValue("matched_rules", transferId);
    }

    private String evaluationReasons(Long transferId) {
        return evaluationValue("reasons", transferId);
    }

    private String evaluationValue(
            String column,
            Long transferId
    ) {
        return jdbc.queryForObject(
                """
                SELECT %s
                FROM fds_evaluation
                WHERE transaction_id = ?
                ORDER BY id DESC
                LIMIT 1
                """.formatted(column),
                String.class,
                transferId
        );
    }

    @AfterEach
    void cleanup() {
        for (String requestKey : requestKeys) {
            List<Long> transferIds = jdbc.queryForList(
                    """
                    SELECT transfer_id
                    FROM bank_transfer
                    WHERE request_key = ?
                    """,
                    Long.class,
                    requestKey
            );

            for (Long transferId : transferIds) {
                jdbc.update(
                        "DELETE FROM fds_evaluation WHERE transaction_id = ?",
                        transferId
                );

                jdbc.update(
                        "DELETE FROM bank_transaction WHERE transfer_id = ?",
                        transferId
                );

                jdbc.update(
                        "DELETE FROM bank_transfer WHERE transfer_id = ?",
                        transferId
                );
            }
        }

        for (int i = createdAccounts.size() - 1; i >= 0; i--) {
            jdbc.update(
                    "DELETE FROM bank_account WHERE account_id = ?",
                    createdAccounts.get(i)
            );
        }

        for (int i = createdUsers.size() - 1; i >= 0; i--) {
            jdbc.update(
                    "DELETE FROM bank_user WHERE bank_user_id = ?",
                    createdUsers.get(i)
            );
        }
    }
}
