package com.github.yehortpk.crawler.models.history;

import com.github.yehortpk.crawler.progress.CrawlerProgressStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.List;

/** One company's part of a stored crawling run. */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CrawlerHistory {
    @Field("crawler_id")
    private int id;
    @Field("crawler_title")
    private String title;
    @Field("crawler_total_pages")
    private int totalPages;
    @Field("crawler_status")
    private CrawlerProgressStatusEnum status;

    @Field("crawled_vacancies")
    private int crawledVacanciesCnt;
    @Field("new_vacancies")
    private int newVacanciesCnt;
    @Field("outdated_vacancies")
    private int outdatedVacanciesCnt;

    @Field("crawler_pages")
    private List<CrawlerPageHistory> pages;
}
