package com.github.yehortpk.crawler.services;

import com.github.yehortpk.crawler.mapper.CrawlingProgressDTOToEntityMapper;
import com.github.yehortpk.crawler.models.history.CrawlingHistory;
import com.github.yehortpk.crawler.progress.CrawlingProgressDTO;
import com.github.yehortpk.crawler.repositories.CrawlingHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * The finished crawling runs, kept in Mongo
 */
@Service
@RequiredArgsConstructor
public class CrawlingHistoryService {
    private final CrawlingHistoryRepository crawlingHistoryRepository;
    private final CrawlingProgressDTOToEntityMapper crawlingProgressMapper;

    public Optional<CrawlingHistory> findByCrawlingHash(String crawlingHash) {
        return crawlingHistoryRepository.findByCrawlingHash(crawlingHash);
    }

    public void saveProgress(CrawlingProgressDTO crawlingProgress) {
        crawlingHistoryRepository.save(crawlingProgressMapper.mapToEntity(crawlingProgress));
    }
}
