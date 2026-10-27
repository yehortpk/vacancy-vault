package com.github.yehortpk.router.repositories;

import com.github.yehortpk.router.models.provider.ChannelProvider;
import com.github.yehortpk.router.models.provider.Provider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProviderRepository extends JpaRepository<Provider, Long> {
    @Query("select p from Provider p where p.code = :code")
    Optional<Provider> findByCode(@Param("code") ChannelProvider code);
}
