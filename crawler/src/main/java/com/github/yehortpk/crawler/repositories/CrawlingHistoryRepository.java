package com.github.yehortpk.crawler.repositories;

import com.github.yehortpk.crawler.models.history.CrawlingHistory;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.Optional;

public interface CrawlingHistoryRepository extends MongoRepository<CrawlingHistory, String> {
    @Query("{ 'crawling_hash': ?0 }")
    Optional<CrawlingHistory> findByCrawlingHash(String crawlingHash);
}
