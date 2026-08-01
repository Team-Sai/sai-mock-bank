package org.teamsai.saimockbank.domain.account.mapper;


import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.teamsai.saimockbank.domain.account.dto.BankAccountDTO;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Mapper
public interface BankAccountMapper {

    List<BankAccountDTO> findAllByUserKey(
            @Param("userKey") String userKey
    );

    Optional<BankAccountDTO> findById(
            @Param("accountId") Long accountId
    );

    Optional<BankAccountDTO> findByBankCodeAndAccountNumber(
            @Param("bankCode") String bankCode,
            @Param("accountNumber") String accountNumber
    );

    Optional<BankAccountDTO> findByIdForUpdate(
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
