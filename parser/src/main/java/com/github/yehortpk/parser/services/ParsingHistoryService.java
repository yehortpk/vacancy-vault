package com.github.yehortpk.parser.services;

import com.github.yehortpk.parser.progress.ParsingProgressDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class ParsingHistoryService {
    private final RestTemplate restTemplate;

    @Value("${router-parser-progress-url}")
    private String routerParserProgressURL;

    public void saveProgress(ParsingProgressDTO parsingProgress) {
        HttpEntity<ParsingProgressDTO> request =
                new HttpEntity<>(parsingProgress);
        restTemplate.exchange(routerParserProgressURL, HttpMethod.POST, request, Void.class);
    }
}
