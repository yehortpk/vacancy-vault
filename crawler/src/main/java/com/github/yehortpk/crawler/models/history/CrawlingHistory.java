package com.github.yehortpk.crawler.models.history;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
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
 * A finished crawling run as it is kept in Mongo, one document per run. It is served in the same
 * JSON shape as the live progress, so the admin panel shows a stored run like the current one.
 */
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "crawling-history")
public class CrawlingHistory {
    @Id
    @JsonIgnore
    private String id;

    @Indexed(unique = true)
    @Field("crawling_hash")
    @JsonProperty("crawling_hash")
    private String crawlingHash;
    @Field("crawled_vacancies")
    @JsonProperty("total")
    private Integer crawledVacancies;
    @Field("new_vacancies")
    @JsonProperty("new")
    private Integer newVacancies;
    @Field("outdated_vacancies")
    @JsonProperty("outdated")
    private Integer outdatedVacancies;
    @Field("status")
    private CrawlingStatusEnum status;
    @Field("finished")
    private Boolean finished;
    @Field("crawlers")
    private List<CrawlerHistory> crawlers;
    @Field("started_at")
    @JsonProperty("started_at")
    private LocalDateTime startedAt;
    @Field("finished_at")
    @JsonProperty("finished_at")
    private LocalDateTime finishedAt;
}
