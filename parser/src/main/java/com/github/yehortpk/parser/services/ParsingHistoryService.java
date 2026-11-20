package com.github.yehortpk.parser.services;

import com.github.yehortpk.parser.mapper.ParsingProgressDTOToEntityMapper;
import com.github.yehortpk.parser.models.history.ParsingHistory;
import com.github.yehortpk.parser.progress.ParsingProgressDTO;
import com.github.yehortpk.parser.repositories.ParsingHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * The finished parsing runs, kept in Mongo
 */
@Service
@RequiredArgsConstructor
public class ParsingHistoryService {
    private final ParsingHistoryRepository parsingHistoryRepository;
    private final ParsingProgressDTOToEntityMapper parsingProgressMapper;

    public Optional<ParsingHistory> findByParsingHash(String parsingHash) {
        return parsingHistoryRepository.findByParsingHash(parsingHash);
    }

    public void saveProgress(ParsingProgressDTO parsingProgress) {
        parsingHistoryRepository.save(parsingProgressMapper.mapToEntity(parsingProgress));
    }
}
