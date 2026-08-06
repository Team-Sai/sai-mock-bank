package org.teamsai.saimockbank.domain.identity.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.teamsai.saimockbank.domain.identity.dto.IdentityDTO;
import org.teamsai.saimockbank.domain.identity.type.IdentityPurpose;

import java.time.LocalDateTime;

@Mapper
public interface IdentityMapper {
    int insert(IdentityDTO identityVerificationDTO);

    IdentityDTO findByIdentityVerificationId(
            @Param("identityVerificationId") String identityVerificationId);

    int updateVerified(
            @Param("identityVerificationId") String identityVerificationId,
            @Param("verifiedAt") LocalDateTime verifiedAt,
            @Param("expiresAt") LocalDateTime expiresAt
    );

    int updateFailed(
            @Param("identityVerificationId") String identityVerificationId,
            @Param("failureReason") String failureReason
    );

    int consume(
            @Param("identityVerificationId") String identityVerificationId,
            @Param("userId") Long userId,
            @Param("purpose") IdentityPurpose purpose
    );
}
