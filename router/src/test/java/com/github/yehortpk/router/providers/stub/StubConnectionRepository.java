package com.github.yehortpk.router.providers.stub;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StubConnectionRepository extends JpaRepository<StubConnection, Long> {
    @Query("select s.connectionId from StubConnection s where s.address = :address")
    Optional<Long> findConnectionIdByAddress(@Param("address") String address);

    @Query("select s from StubConnection s where s.connectionId in :connectionIds")
    List<StubConnection> findByConnectionIds(@Param("connectionIds") Collection<Long> connectionIds);

    @Query("select c.id from Connection c where c.provider.id = :providerId and not exists"
            + " (select s.connectionId from StubConnection s where s.connectionId = c.id)")
    List<Long> findConnectionIdsWithoutDetails(@Param("providerId") long providerId);
}
