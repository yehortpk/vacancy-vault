package com.github.yehortpk.crawler.progress;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor
@Getter
@AllArgsConstructor
@Builder
public class CrawlingProgressDTO {
    @JsonProperty("crawling_hash")
    private String crawlingHash;
    @Builder.Default
    private CrawlingStatusEnum status = CrawlingStatusEnum.IDLE;
    @Builder.Default
    private boolean finished = false;
    @Setter
    @Builder.Default
    private List<CrawlerProgress> crawlers = new ArrayList<>();

    @JsonProperty("total")
    @Builder.Default
    private int crawledVacanciesTotalCount = 0;
    @JsonProperty("new")
    @Builder.Default
    private int newVacanciesTotalCount = 0;
    @JsonProperty("outdated")
    @Builder.Default
    private int outdatedVacanciesTotalCount = 0;
    @JsonProperty("started_at")
    private LocalDateTime startedAt;
    @JsonProperty("finished_at")
    private LocalDateTime finishedAt;
}
