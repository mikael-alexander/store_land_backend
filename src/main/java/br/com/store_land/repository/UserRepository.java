package br.com.store_land.repository;

import br.com.store_land.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    @Query("SELECT u FROM User u WHERE LOWER(u.username) = LOWER(:identifier) OR LOWER(u.email) = LOWER(:identifier)")
    Optional<User> findByUsernameOrEmail(@Param("identifier") String identifier);

    @Query("SELECT u FROM User u WHERE LOWER(u.email) = LOWER(:email) AND u.isActive = true")
    Optional<User> findByEmailActive(@Param("email") String email);

    @Query("SELECT u FROM User u WHERE LOWER(u.username) = LOWER(:username)")
    Optional<User> findByUsername(@Param("username") String username);

    @Query("SELECT u FROM User u WHERE LOWER(u.username) = LOWER(:username) AND u.isActive = false")
    Optional<User> findInactiveByUsername(@Param("username") String username);

    @Modifying
    @Query("UPDATE User u SET u.passwordHash = :passwordHash, u.updatedAt = :updatedAt WHERE u.id = :userId")
    void updatePassword(@Param("userId") Long userId, @Param("passwordHash") String passwordHash, @Param("updatedAt") OffsetDateTime updatedAt);
}
