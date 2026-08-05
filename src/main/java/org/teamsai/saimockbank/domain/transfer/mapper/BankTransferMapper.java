package org.teamsai.saimockbank.domain.transfer.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.teamsai.saimockbank.domain.transfer.dto.BankTransferDTO;

import java.util.Optional;

@Mapper
public interface BankTransferMapper {

    Optional<BankTransferDTO> findById(
            @Param("transferId") Long transferId
    );

    Optional<BankTransferDTO> findByRequestKey(
            @Param("requestKey") String requestKey
    );

    int insert(BankTransferDTO transfer);
}
