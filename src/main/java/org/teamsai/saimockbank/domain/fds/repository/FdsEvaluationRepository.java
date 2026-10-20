package org.teamsai.saimockbank.domain.fds.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.teamsai.saimockbank.domain.fds.entity.FdsEvaluation;
import org.teamsai.saimockbank.domain.fds.type.FdsRiskLevel;

import java.util.List;

public interface FdsEvaluationRepository
        extends JpaRepository<FdsEvaluation, Long> {

    List<FdsEvaluation> findAllByOrderByEvaluatedAtDesc();

    List<FdsEvaluation> findByTransactionIdOrderByEvaluatedAtDesc(
            Long transactionId
    );

    List<FdsEvaluation> findByRiskLevelOrderByEvaluatedAtDesc(
            FdsRiskLevel riskLevel
    );
}
