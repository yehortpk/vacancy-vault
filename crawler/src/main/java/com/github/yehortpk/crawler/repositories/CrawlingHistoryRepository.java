package com.github.yehortpk.crawler.repositories;

import com.github.yehortpk.crawler.models.history.CrawlingHistory;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CrawlingHistoryRepository extends MongoRepository<CrawlingHistory, String> {
    @Query("{ 'crawling_hash': ?0 }")
    Optional<CrawlingHistory> findByCrawlingHash(String crawlingHash);

    /**
     * Every stored run, newest first, without its companies: those carry every page's logs and
     * are only needed once a single run is opened.
     */
    @Query(value = "{}", fields = "{ 'crawlers': 0 }", sort = "{ 'finished_at': -1 }")
    List<CrawlingHistory> findSummaries();
}
