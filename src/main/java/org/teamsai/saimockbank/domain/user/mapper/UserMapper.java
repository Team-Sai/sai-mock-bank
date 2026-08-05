package org.teamsai.saimockbank.domain.user.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.teamsai.saimockbank.domain.user.dto.UserDTO;

import java.time.LocalDateTime;
import java.util.Optional;

@Mapper
public interface UserMapper {
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
    Optional<UserDTO> findById(@Param("bankUserId") Long bankUserId);
    Optional<UserDTO> findByNameAndUserToken(@Param("name") String name, @Param("userToken") String userToken);
    int updateUserKey(@Param("bankUserId") Long bankUserId,
                       @Param("userKeyHash") String userKeyHash,
                       @Param("issuedAt") LocalDateTime issuedAt);
    int deleteByUserId(Long userId);

    boolean existsByUserToken(
            @Param("userToken") String userToken
    );

    String findUserKeyByUserId(
            @Param("userId") Long userId
    );
}
