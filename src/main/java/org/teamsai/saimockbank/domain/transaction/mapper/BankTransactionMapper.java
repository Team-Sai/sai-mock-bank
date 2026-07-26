package org.teamsai.saimockbank.domain.transaction.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.teamsai.saimockbank.domain.transaction.entity.BankTransaction;

import java.util.List;
import java.util.Optional;

@Mapper
public interface BankTransactionMapper {
    Optional<BankTransaction> findById(
            @Param("transactionId") Long transactionId
    );

    Optional<BankTransaction> findByTransactionKey(
            @Param("transactionKey") String transactionKey
    );

    List<BankTransaction> findAllByAccountId(
            @Param("accountId") Long accountId
    );
}
