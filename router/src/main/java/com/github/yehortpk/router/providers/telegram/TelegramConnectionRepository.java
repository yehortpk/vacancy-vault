package com.github.yehortpk.router.providers.telegram;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TelegramConnectionRepository extends JpaRepository<TelegramConnection, Long> {
    @Query("select t.connectionId from TelegramConnection t where t.chatId = :chatId")
    Optional<Long> findConnectionIdByChatId(@Param("chatId") long chatId);

    @Query("select t from TelegramConnection t where t.connectionId in :connectionIds")
    List<TelegramConnection> findByConnectionIds(@Param("connectionIds") Collection<Long> connectionIds);

    @Query("select c.id from Connection c where c.provider.id = :providerId and not exists"
            + " (select t.connectionId from TelegramConnection t where t.connectionId = c.id)")
    List<Long> findConnectionIdsWithoutDetails(@Param("providerId") long providerId);
}
