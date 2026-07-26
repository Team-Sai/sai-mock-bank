package org.teamsai.saimockbank.domain.account.mapper;


import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.teamsai.saimockbank.domain.account.entity.BankAccount;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Mapper
public interface BankAccountMapper {

    List<BankAccount> findAllByUserKey(
            @Param("userKey") String userKey
    );

    Optional<BankAccount> findById(
            @Param("accountId") Long accountId
    );

    Optional<BankAccount> findByBankCodeAndAccountNumber(
            @Param("bankCode") String bankCode,
            @Param("accountNumber") String accountNumber
    );

    Optional<BankAccount> findByIdForUpdate(
            @Param("accountId") Long accountId
    );

    int increaseBalance(
            @Param("accountId") Long accountId,
            @Param("amount")BigDecimal amount
            );
    int decreaseBalance(
            @Param("accountId") Long accountId,
            @Param("amount")BigDecimal amount
            );
}
