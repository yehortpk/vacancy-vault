package com.github.yehortpk.router.repositories;

import com.github.yehortpk.router.models.provider.TelegramConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TelegramConnectionRepository extends JpaRepository<TelegramConnection, Long> {
    @Query("select c from TelegramConnection c where c.chatId = :chatId")
    Optional<TelegramConnection> findByChatId(@Param("chatId") long chatId);

    @Query("select c from TelegramConnection c where c.user.id = :userId order by c.linkedAt")
    List<TelegramConnection> findByUserId(@Param("userId") long userId);

    /**
     * Scoped by owner on purpose: filter_provider.connection_id carries no foreign key, so
     * nothing in the database stops one user binding a filter to another user's chat. Every
     * lookup that precedes a binding write goes through here.
     */
    @Query("select c from TelegramConnection c where c.id = :id and c.user.id = :userId")
    Optional<TelegramConnection> findByIdAndUserId(@Param("id") long id, @Param("userId") long userId);

    @Query("select c from TelegramConnection c where c.id in :ids")
    List<TelegramConnection> findByIds(@Param("ids") List<Long> ids);
}
