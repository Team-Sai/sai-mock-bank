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
            @Param("pendingExpiresAt") LocalDateTime pendingExpiresAt,
            @Param("operationId") String operationId
    );

    int revokeUserKey(
            @Param("hashedKey") String hashedKey
    );

    record KeyRecoveryState(Long bankUserId, String activeKey, String pendingKey,
                            String recoveryPreviousKey, LocalDateTime recoveryExpiresAt,
                            String operationId, String rotationKeyHash) {}

    Optional<KeyRecoveryState> findKeyRecoveryStateForUpdate(@Param("userToken") String userToken);

    int recoverKeyState(
            @Param("bankUserId") Long bankUserId,
            @Param("previousHashedKey") String previousHashedKey,
            @Param("operationId") String operationId);

    boolean isRecoveryExpired(@Param("bankUserId") Long bankUserId);

    record KeyOperation(String keyHash, String previousKeyHash, LocalDateTime issuedAt, boolean recovered) {}

    Optional<KeyOperation> findKeyOperationForUpdate(@Param("bankUserId") Long bankUserId,
                                                    @Param("operationId") String operationId);

    int insertKeyOperation(@Param("bankUserId") Long bankUserId, @Param("operationId") String operationId,
                           @Param("keyHash") String keyHash, @Param("previousKeyHash") String previousKeyHash,
                           @Param("issuedAt") LocalDateTime issuedAt);

    int saveRecoveryReceipt(@Param("bankUserId") Long bankUserId, @Param("operationId") String operationId,
                            @Param("keyHash") String keyHash, @Param("previousKeyHash") String previousKeyHash);

    Optional<UserDTO> findByIdForUpdate(@Param("bankUserId") Long bankUserId);

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
            @Param("hashedKey") String hashedKey,
            @Param("operationId") String operationId
    );

    int markPendingExpired(
            @Param("bankUserId") Long bankUserId,
            @Param("pendingIssuedAt") LocalDateTime pendingIssuedAt
    );
}
