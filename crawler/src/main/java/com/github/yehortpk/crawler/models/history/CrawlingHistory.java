package com.github.yehortpk.crawler.models.history;

import com.github.yehortpk.crawler.progress.CrawlingStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;
import java.util.List;

/**
 * A finished crawling run as it is kept in Mongo, one document per run.
 */
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "crawling-history")
public class CrawlingHistory {
    @Id
    private String id;

    @Indexed(unique = true)
    @Field("crawling_hash")
    private String crawlingHash;
    @Field("crawled_vacancies")
    private Integer crawledVacancies;
    @Field("new_vacancies")
    private Integer newVacancies;
    @Field("outdated_vacancies")
    private Integer outdatedVacancies;
    @Field("status")
    private CrawlingStatusEnum status;
    @Field("finished")
    private Boolean finished;
    @Field("crawlers")
    private List<CrawlerHistory> crawlers;
    @Field("started_at")
    private LocalDateTime startedAt;
    @Field("finished_at")
    private LocalDateTime finishedAt;
}
