package com.github.yehortpk.parser.repositories;

import com.github.yehortpk.parser.models.history.ParsingHistory;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.Optional;

public interface ParsingHistoryRepository extends MongoRepository<ParsingHistory, String> {
    @Query("{ 'parsing_hash': ?0 }")
    Optional<ParsingHistory> findByParsingHash(String parsingHash);
}
