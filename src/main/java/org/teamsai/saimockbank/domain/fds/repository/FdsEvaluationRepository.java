package org.teamsai.saimockbank.domain.fds.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.teamsai.saimockbank.domain.fds.entity.FdsEvaluation;
import org.teamsai.saimockbank.domain.fds.type.FdsRiskLevel;

import java.util.Optional;

public interface FdsEvaluationRepository
        extends JpaRepository<FdsEvaluation, Long> {

    Page<FdsEvaluation> findAllByOrderByEvaluatedAtDesc(
            Pageable pageable
    );

    Page<FdsEvaluation> findByTransactionIdOrderByEvaluatedAtDesc(
            Long transactionId,
            Pageable pageable
    );

    Page<FdsEvaluation> findByRiskLevelOrderByEvaluatedAtDesc(
            FdsRiskLevel riskLevel,
            Pageable pageable
    );

    Optional<FdsEvaluation> findFirstByTransactionIdOrderByEvaluatedAtDescIdDesc(
            Long transactionId
    );
}