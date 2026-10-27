package com.github.yehortpk.router.repositories;

import com.github.yehortpk.router.models.user.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<AppUser, Long> {
    @Query("select u from AppUser u where u.email = :email")
    Optional<AppUser> findByEmail(@Param("email") String email);

    @Query("select count(u) > 0 from AppUser u where u.email = :email")
    boolean existsByEmail(@Param("email") String email);

    @Query("select u from AppUser u where u.apiKeyPrefix = :prefix")
    Optional<AppUser> findByApiKeyPrefix(@Param("prefix") String prefix);
}
