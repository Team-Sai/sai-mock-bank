
package org.teamsai.saimockbank.domain.fds;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.context.ActiveProfiles;
import org.teamsai.saimockbank.domain.fds.service.FdsAdminReviewService;
import org.teamsai.saimockbank.domain.transfer.dto.TransferRequest;
import org.teamsai.saimockbank.domain.transfer.dto.TransferResponse;
import org.teamsai.saimockbank.domain.transfer.dto.TransferStatus;
import org.teamsai.saimockbank.domain.transfer.service.BankTransferService;
import org.teamsai.saimockbank.global.exception.DomainException;

import java.math.BigDecimal;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("dev")
class FdsScenarioIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private BankTransferService transferService;

    @Autowired
    private FdsAdminReviewService reviewService;

    private final List<Long> createdUsers = new ArrayList<>();
    private final List<Long> createdAccounts = new ArrayList<>();
    private final List<Long> createdTransfers = new ArrayList<>();

    private final String runId =
            UUID.randomUUID().toString().replace("-", "").substring(0, 12);

    private record Account(
            Long id,
            String number
    ) {
    }

    private record Scenario(
            Long senderUserId,
            Long adminUserId,
            Account sender,
            Account receiver,
            String requestKey
    ) {
    }

    @Test
    void HIGH_보류_관리자_승인_거절_전체_자동화() {

        Scenario approval = prepareScenario("approve");

        BigDecimal senderBefore = balance(approval.sender().id());
        BigDecimal receiverBefore = balance(approval.receiver().id());

        TransferResponse held = executeHighTransfer(approval);

        assertThat(held.status())
                .isEqualTo(TransferStatus.PENDING_REVIEW);

        assertThat(held.completedAt()).isNull();

        assertThat(balance(approval.sender().id()))
                .isEqualByComparingTo(senderBefore);

        assertThat(balance(approval.receiver().id()))
                .isEqualByComparingTo(receiverBefore);

        assertThat(transactionCount(held.transferId()))
                .isZero();

        assertThat(evaluationScore(held.transferId()))
                .isEqualTo(90);

        assertThat(evaluationAction(held.transferId()))
                .isEqualTo("HOLD");

        var approved = reviewService.approve(
                held.transferId(),
                approval.adminUserId(),
                "자동화 승인 테스트"
        );

        assertThat(approved.status())
                .isEqualTo(TransferStatus.SUCCESS);

        assertThat(balance(approval.sender().id()))
                .isEqualByComparingTo(
                        senderBefore.subtract(
                                BigDecimal.valueOf(500_000)
                        )
                );

        assertThat(balance(approval.receiver().id()))
                .isEqualByComparingTo(
                        receiverBefore.add(
                                BigDecimal.valueOf(500_000)
                        )
                );

        assertThat(transactionCount(held.transferId()))
                .isEqualTo(2);

        assertThatThrownBy(() ->
                reviewService.approve(
                        held.transferId(),
                        approval.adminUserId(),
                        "중복 승인"
                )
        ).isInstanceOf(DomainException.class);

        assertThat(transactionCount(held.transferId()))
                .isEqualTo(2);

        System.out.println(
                "[PASS] HIGH 보류 → 관리자 승인 → 중복 승인 차단"
        );

        Scenario rejection = prepareScenario("reject");

        BigDecimal rejectSenderBefore =
                balance(rejection.sender().id());

        BigDecimal rejectReceiverBefore =
                balance(rejection.receiver().id());

        TransferResponse heldForReject =
                executeHighTransfer(rejection);

        assertThat(heldForReject.status())
                .isEqualTo(TransferStatus.PENDING_REVIEW);

        assertThat(evaluationScore(heldForReject.transferId()))
                .isEqualTo(90);

        var rejected = reviewService.reject(
                heldForReject.transferId(),
                rejection.adminUserId(),
                "자동화 거절 테스트"
        );

        assertThat(rejected.status())
                .isEqualTo(TransferStatus.REJECTED);

        assertThat(balance(rejection.sender().id()))
                .isEqualByComparingTo(rejectSenderBefore);

        assertThat(balance(rejection.receiver().id()))
                .isEqualByComparingTo(rejectReceiverBefore);

        assertThat(transactionCount(heldForReject.transferId()))
                .isZero();

        assertThatThrownBy(() ->
                reviewService.reject(
                        heldForReject.transferId(),
                        rejection.adminUserId(),
                        "중복 거절"
                )
        ).isInstanceOf(DomainException.class);

        System.out.println(
                "[PASS] HIGH 보류 → 관리자 거절 → 중복 거절 차단"
        );
    }

    private record ReviewResult(
            TransferStatus status,
            Throwable error
    ) {
    }

    @Test
    void 동일_보류거래_동시승인_한번만_이체된다() throws Exception {
        Scenario scenario = prepareScenario("concurrent-approve");
        TransferResponse held = executeHighTransfer(scenario);

        assertThat(held.status()).isEqualTo(TransferStatus.PENDING_REVIEW);

        BigDecimal senderBefore = balance(scenario.sender().id());
        BigDecimal receiverBefore = balance(scenario.receiver().id());

        List<ReviewResult> results = runConcurrentReviews(
                () -> reviewService.approve(
                        held.transferId(), scenario.adminUserId(), "동시 승인 A"
                ).status(),
                () -> reviewService.approve(
                        held.transferId(), scenario.adminUserId(), "동시 승인 B"
                ).status()
        );

        assertThat(results.stream().filter(r -> r.status() == TransferStatus.SUCCESS).count())
                .isEqualTo(1);
        assertThat(results.stream().filter(r -> r.error() instanceof DomainException).count())
                .isEqualTo(1);
        assertThat(results.stream().filter(r -> r.error() != null).count())
                .isEqualTo(1);

        assertThat(transferStatus(held.transferId()))
                .isEqualTo(TransferStatus.SUCCESS.name());
        assertThat(transactionCount(held.transferId())).isEqualTo(2);
        assertThat(balance(scenario.sender().id()))
                .isEqualByComparingTo(senderBefore.subtract(BigDecimal.valueOf(500_000)));
        assertThat(balance(scenario.receiver().id()))
                .isEqualByComparingTo(receiverBefore.add(BigDecimal.valueOf(500_000)));

        System.out.println("[PASS] 동시 승인 2건 → 승인 1건, 중복 차단 1건, 이체 1회");
    }

    @Test
    void 동일_보류거래_승인거절_경합시_하나만_반영된다() throws Exception {
        Scenario scenario = prepareScenario("concurrent-mixed");
        TransferResponse held = executeHighTransfer(scenario);

        assertThat(held.status()).isEqualTo(TransferStatus.PENDING_REVIEW);

        BigDecimal senderBefore = balance(scenario.sender().id());
        BigDecimal receiverBefore = balance(scenario.receiver().id());

        List<ReviewResult> results = runConcurrentReviews(
                () -> reviewService.approve(
                        held.transferId(), scenario.adminUserId(), "동시 승인"
                ).status(),
                () -> reviewService.reject(
                        held.transferId(), scenario.adminUserId(), "동시 거절"
                ).status()
        );

        assertThat(results.stream().filter(r -> r.status() != null).count())
                .isEqualTo(1);
        assertThat(results.stream().filter(r -> r.error() instanceof DomainException).count())
                .isEqualTo(1);
        assertThat(results.stream().filter(r -> r.error() != null).count())
                .isEqualTo(1);

        String finalStatus = transferStatus(held.transferId());
        assertThat(finalStatus).isIn("SUCCESS", "REJECTED");

        if ("SUCCESS".equals(finalStatus)) {
            assertThat(results.stream().filter(r -> r.status() == TransferStatus.SUCCESS).count())
                    .isEqualTo(1);
            assertThat(transactionCount(held.transferId())).isEqualTo(2);
            assertThat(balance(scenario.sender().id()))
                    .isEqualByComparingTo(senderBefore.subtract(BigDecimal.valueOf(500_000)));
            assertThat(balance(scenario.receiver().id()))
                    .isEqualByComparingTo(receiverBefore.add(BigDecimal.valueOf(500_000)));
        } else {
            assertThat(results.stream().filter(r -> r.status() == TransferStatus.REJECTED).count())
                    .isEqualTo(1);
            assertThat(transactionCount(held.transferId())).isZero();
            assertThat(balance(scenario.sender().id()))
                    .isEqualByComparingTo(senderBefore);
            assertThat(balance(scenario.receiver().id()))
                    .isEqualByComparingTo(receiverBefore);
        }

        System.out.println("[PASS] 승인·거절 동시 요청 → " + finalStatus + " 한 건만 반영");
    }

    private List<ReviewResult> runConcurrentReviews(
            Callable<TransferStatus> first,
            Callable<TransferStatus> second
    ) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Callable<ReviewResult> firstTask = () -> runReview(first, ready, start);
            Callable<ReviewResult> secondTask = () -> runReview(second, ready, start);
            Future<ReviewResult> firstFuture = executor.submit(firstTask);
            Future<ReviewResult> secondFuture = executor.submit(secondTask);

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            return List.of(
                    firstFuture.get(30, TimeUnit.SECONDS),
                    secondFuture.get(30, TimeUnit.SECONDS)
            );
        } finally {
            start.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
    }

    private ReviewResult runReview(
            Callable<TransferStatus> operation,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        start.await();

        try {
            return new ReviewResult(operation.call(), null);
        } catch (Exception exception) {
            return new ReviewResult(null, exception);
        }
    }

    private String transferStatus(Long transferId) {
        return jdbc.queryForObject(
                "SELECT status FROM bank_transfer WHERE transfer_id = ?",
                String.class,
                transferId
        );
    }

    private Scenario prepareScenario(String label) {

        Long senderUser = createUser(label + "-sender", "USER");
        Long receiverUser = createUser(label + "-receiver", "USER");
        Long adminUser = createUser(label + "-admin", "ADMIN");

        Account sender = createAccount(senderUser, label + "-sender");
        Account oldRecipient =
                createAccount(receiverUser, label + "-old");

        Account recipientA =
                createAccount(receiverUser, label + "-a");

        Account recipientB =
                createAccount(receiverUser, label + "-b");

        Account recipientC =
                createAccount(receiverUser, label + "-c");

        LocalDateTime now = LocalDateTime.now();

        LocalDateTime oldTime = now.minusMinutes(20);
        LocalDateTime depositTime = now.minusMinutes(2);
        LocalDateTime recentTime = now.minusSeconds(90);

        for (int i = 0; i < 4; i++) {
            seedTransfer(
                    sender.id(),
                    oldRecipient.id(),
                    10_000,
                    oldTime.plusSeconds(i)
            );
        }

        seedTransfer(
                oldRecipient.id(),
                sender.id(),
                2_000_000,
                depositTime
        );

        for (int i = 0; i < 3; i++) {
            seedTransfer(
                    sender.id(),
                    oldRecipient.id(),
                    100_000,
                    recentTime.plusSeconds(i)
            );
        }

        seedTransfer(
                sender.id(),
                recipientA.id(),
                500_000,
                recentTime.plusSeconds(4)
        );

        seedTransfer(
                sender.id(),
                recipientB.id(),
                500_000,
                recentTime.plusSeconds(5)
        );

        System.out.println(
                "[READY] " + label
                        + " sender=" + sender.id()
                        + " receiver=" + recipientC.id()
        );

        return new Scenario(
                senderUser,
                adminUser,
                sender,
                recipientC,
                "fds-auto-" + runId + "-" + label
        );
    }

    private TransferResponse executeHighTransfer(
            Scenario scenario
    ) {
        TransferRequest request = new TransferRequest(
                scenario.requestKey(),
                scenario.sender().id(),
                scenario.receiver().number(),
                BigDecimal.valueOf(500_000),
                "FDS 자동 검증",
                "FDS 자동 검증"
        );

        TransferResponse response = transferService.transfer(
                scenario.senderUserId(),
                request
        );

        createdTransfers.add(response.transferId());

        System.out.println(
                "[FDS] transferId=" + response.transferId()
                        + ", status=" + response.status()
        );

        return response;
    }

    private Long createUser(String label, String role) {

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
                "FDS-" + label,
                "fds-" + unique + "@test.local",
                "test-only",
                "fds-" + unique,
                java.sql.Date.valueOf("2000-01-01"),
                role
        );

        createdUsers.add(id);
        return id;
    }

    private Account createAccount(
            Long userId,
            String label
    ) {
        String number = "FDS" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 16);

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
                "FDS-" + label,
                "테스트",
                BigDecimal.valueOf(5_000_000),
                "ACTIVE"
        );

        createdAccounts.add(id);
        return new Account(id, number);
    }

    private void seedTransfer(
            Long fromAccountId,
            Long toAccountId,
            long amount,
            LocalDateTime completedAt
    ) {
        Long id = insertAndGetId(
                """
                INSERT INTO bank_transfer (
                    request_key,
                    from_account_id,
                    to_account_id,
                    amount,
                    status,
                    completed_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                """,
                "fds-seed-" + UUID.randomUUID(),
                fromAccountId,
                toAccountId,
                BigDecimal.valueOf(amount),
                "SUCCESS",
                completedAt
        );

        createdTransfers.add(id);
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
                """
                SELECT balance
                FROM bank_account
                WHERE account_id = ?
                """,
                BigDecimal.class,
                accountId
        );
    }

    private long transactionCount(Long transferId) {
        Long count = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM bank_transaction
                WHERE transfer_id = ?
                """,
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

    private String evaluationAction(Long transferId) {
        return jdbc.queryForObject(
                """
                SELECT action
                FROM fds_evaluation
                WHERE transaction_id = ?
                ORDER BY id DESC
                LIMIT 1
                """,
                String.class,
                transferId
        );
    }

    @AfterEach
    void cleanup() {

        for (int i = createdTransfers.size() - 1; i >= 0; i--) {
            Long id = createdTransfers.get(i);

            jdbc.update(
                    "DELETE FROM fds_evaluation WHERE transaction_id = ?",
                    id
            );

            jdbc.update(
                    "DELETE FROM bank_transaction WHERE transfer_id = ?",
                    id
            );

            jdbc.update(
                    "DELETE FROM bank_transfer WHERE transfer_id = ?",
                    id
            );
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
