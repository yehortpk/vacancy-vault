package com.github.yehortpk.parser.repositories;

import com.github.yehortpk.parser.models.admin.Admin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AdminRepository extends JpaRepository<Admin, Long> {
    @Query("select a from Admin a where a.username = :username")
    Optional<Admin> findByUsername(@Param("username") String username);
}
