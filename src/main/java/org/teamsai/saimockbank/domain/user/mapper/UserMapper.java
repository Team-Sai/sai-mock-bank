package org.teamsai.saimockbank.domain.user.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.teamsai.saimockbank.domain.user.dto.UserDTO;

import java.time.LocalDateTime;
import java.util.Optional;

@Mapper
public interface UserMapper {
    int savePendingUserKey(
            @Param("bankUserId") Long bankUserId,
            @Param("pendingUserKey") String pendingUserKey,
            @Param("pendingIssuedAt") LocalDateTime pendingIssuedAt,
            @Param("pendingExpiresAt") LocalDateTime pendingExpiresAt
    );

    int revokeUserKey(
            @Param("hashedKey") String hashedKey
    );

    record KeyRecoveryState(Long bankUserId, String activeKey, String pendingKey) {}

    Optional<KeyRecoveryState> findKeyRecoveryStateForUpdate(@Param("userToken") String userToken);

    int recoverKeyState(
            @Param("bankUserId") Long bankUserId,
            @Param("previousHashedKey") String previousHashedKey);

    int insert(UserDTO user);

    boolean existsByEmail(
            @Param("email") String email
    );
    Optional<UserDTO> findByEmail(
            @Param("email") String email
    );
    Optional<UserDTO> findByUserToken(
            @Param("userToken") String userToken
    );
    Optional<UserDTO> findById(
            @Param("bankUserId") Long bankUserId
    );
    Optional<UserDTO> findByNameAndUserToken(@Param("name") String name, @Param("userToken") String userToken);

    int deleteByUserId(@Param("userId") Long userId);

    boolean existsByUserToken(
            @Param("userToken") String userToken
    );

    int promotePendingToActive(
            @Param("hashedKey") String hashedKey
    );

    int markPendingExpired(
            @Param("bankUserId") Long bankUserId,
            @Param("pendingIssuedAt") LocalDateTime pendingIssuedAt
    );
}
