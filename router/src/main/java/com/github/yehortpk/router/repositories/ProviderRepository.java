package com.github.yehortpk.router.repositories;

import com.github.yehortpk.router.models.provider.Provider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ProviderRepository extends JpaRepository<Provider, Long> {
    @Query("select p from Provider p where p.enabled order by p.title")
    List<Provider> findEnabled();
}
