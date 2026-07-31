package org.teamsai.saimockbank.domain.test_identity.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.teamsai.saimockbank.domain.test_identity.entity.TestIdentity;

import java.time.LocalDateTime;

@Mapper
public interface TestIdentityMapper {
    TestIdentity findByNameAndEmail(@Param("name") String name, @Param("email") String email);
    void updateUserKey(@Param("identityId") Long identityId,
                       @Param("userKey") String userKey,
                       @Param("issuedAt") LocalDateTime issuedAt);
}
