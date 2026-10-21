package org.teamsai.saimockbank.domain.fds.entity;

import jakarta.persistence.*;
import org.teamsai.saimockbank.domain.fds.type.FdsAction;
import org.teamsai.saimockbank.domain.fds.type.FdsRiskLevel;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "fds_evaluation",
        indexes = {
                @Index(
                        name = "idx_fds_transaction",
                        columnList = "transaction_id"
                ),
                @Index(
                        name = "idx_fds_evaluated_at",
                        columnList = "evaluated_at"
                )
        }
)
public class FdsEvaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id")
    private Long transactionId;

    @Column(name = "sender_account_id", nullable = false)
    private Long senderAccountId;

    @Column(name = "receiver_account_id", nullable = false)
    private Long receiverAccountId;

    @Column(
            name = "amount",
            nullable = false,
            precision = 19,
            scale = 2
    )
    private BigDecimal amount;

    @Column(name = "risk_score", nullable = false)
    private int riskScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 20)
    private FdsRiskLevel riskLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 20)
    private FdsAction action;

    @Column(name = "matched_rules", length = 255)
    private String matchedRules;

    @Lob
    @Column(name = "reasons")
    private String reasons;

    @Column(name = "evaluated_at", nullable = false)
    private LocalDateTime evaluatedAt;

    protected FdsEvaluation() {
    }

    public FdsEvaluation(
            Long transactionId,
            Long senderAccountId,
            Long receiverAccountId,
            BigDecimal amount,
            int riskScore,
            FdsRiskLevel riskLevel,
            FdsAction action,
            String matchedRules,
            String reasons,
            LocalDateTime evaluatedAt
    ) {
        this.transactionId = transactionId;
        this.senderAccountId = senderAccountId;
        this.receiverAccountId = receiverAccountId;
        this.amount = amount;
        this.riskScore = riskScore;
        this.riskLevel = riskLevel;
        this.action = action;
        this.matchedRules = matchedRules;
        this.reasons = reasons;
        this.evaluatedAt = evaluatedAt;
    }

    /**
     * 이체 완료 후 FDS 평가 이력에 거래 ID 연결
     */
    public void linkTransaction(Long transactionId) {

        if (transactionId == null || transactionId <= 0) {
            throw new IllegalArgumentException(
                    "유효한 거래 ID가 필요합니다."
            );
        }

        if (this.transactionId != null) {
            throw new IllegalStateException(
                    "이미 거래가 연결된 FDS 평가입니다."
            );
        }

        this.transactionId = transactionId;
    }

    public Long getId() {
        return id;
    }

    public Long getTransactionId() {
        return transactionId;
    }

    public Long getSenderAccountId() {
        return senderAccountId;
    }

    public Long getReceiverAccountId() {
        return receiverAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public int getRiskScore() {
        return riskScore;
    }

    public FdsRiskLevel getRiskLevel() {
        return riskLevel;
    }

    public FdsAction getAction() {
        return action;
    }

    public String getMatchedRules() {
        return matchedRules;
    }

    public String getReasons() {
        return reasons;
    }

    public LocalDateTime getEvaluatedAt() {
        return evaluatedAt;
    }
}