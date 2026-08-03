package org.teamsai.saimockbank.domain.identity.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.teamsai.saimockbank.domain.identity.dto.IdentityDTO;

import java.time.LocalDateTime;
import java.util.Optional;

@Mapper
public interface IdentityMapper {
    Optional<IdentityDTO> findByNameAndUserToken(@Param("name") String name, @Param("userToken") String userToken);
    int updateUserKey(@Param("identityId") Long identityId,
                       @Param("userKeyHash") String userKeyHash,
                       @Param("issuedAt") LocalDateTime issuedAt);
}
