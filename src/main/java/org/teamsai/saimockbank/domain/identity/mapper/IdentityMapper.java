package org.teamsai.saimockbank.domain.identity.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.teamsai.saimockbank.domain.identity.dto.IdentityDTO;

import javax.swing.text.html.Option;
import java.time.LocalDateTime;
import java.util.Optional;

@Mapper
public interface IdentityMapper {
    Optional<IdentityDTO> findByNameAndUserToken(@Param("name") String name, @Param("userToken") String userToken);
    void updateUserKey(@Param("identityId") Long identityId,
                       @Param("userKeyHash") String userKeyHash,
                       @Param("userKey") String userKey,
                       @Param("issuedAt") LocalDateTime issuedAt);
}
