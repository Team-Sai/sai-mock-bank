package org.teamsai.saimockbank.domain.transaction.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.teamsai.saimockbank.domain.transaction.dto.BankTransactionDTO;

import java.util.List;
import java.util.Optional;

@Mapper
public interface BankTransactionMapper {
    Optional<BankTransactionDTO> findById(
            @Param("transactionId") Long transactionId
    );

    Optional<BankTransactionDTO> findByTransactionKey(
            @Param("transactionKey") String transactionKey
    );

    List<BankTransactionDTO> findAllByAccountId(
            @Param("accountId") Long accountId
    );

    int insert(BankTransactionDTO transaction);

    List<BankTransactionDTO> findAllByAccountIdAfter(
            @Param("accountId") Long accountId,
            @Param("afterTransactionId") Long afterTransactionId
    );
}
