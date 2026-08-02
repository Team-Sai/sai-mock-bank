package org.teamsai.saimockbank.domain.account.mapper;


import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.teamsai.saimockbank.domain.account.dto.AccountWithOwnerDTO;
import org.teamsai.saimockbank.domain.account.dto.BankAccountDTO;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Mapper
public interface BankAccountMapper {
    int insert(BankAccountDTO bankAccountDTO);

    List<BankAccountDTO> findAllByUserKey(
            @Param("userKey") String userKey
    );

    Optional<BankAccountDTO> findById(
            @Param("accountId") Long accountId
    );

    Optional<BankAccountDTO> findByIdForUpdate(
            @Param("accountId") Long accountId
    );

    List<BankAccountDTO> findByUserKey(
            @Param("userKey") String userKey
    );

    Optional<String> findOwnerUserKeyHashByAccountId(@Param("accountId") Long accountId);

    Optional<AccountWithOwnerDTO> findByIdWithOwner(@Param("accountId") Long accountId);

    int increaseBalance(
            @Param("accountId") Long accountId,
            @Param("amount")BigDecimal amount
            );
    int decreaseBalance(
            @Param("accountId") Long accountId,
            @Param("amount")BigDecimal amount
            );
}
