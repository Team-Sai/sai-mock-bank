package org.teamsai.saimockbank.domain.transfer.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.teamsai.saimockbank.domain.transfer.entity.BankTransfer;

import java.util.Optional;

@Mapper
public interface BankTransferMapper {

    Optional<BankTransfer> findById(
            @Param("transferId") Long transferId
    );

    Optional<BankTransfer> findByRequestKey(
            @Param("requestKey") String requestKey
    );
}
