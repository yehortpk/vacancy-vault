package com.github.yehortpk.crawler.models.history;

import com.github.yehortpk.crawler.progress.PageProgressStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CrawlerPageHistory {
    @Field("page_id")
    private int id;
    @Field("page_status")
    private PageProgressStatusEnum status;
    @Field("page_crawled_vacancies")
    private int crawledVacanciesCnt;
    @Field("page_logs")
    private List<CrawlerPageLog> logs = new ArrayList<>();
}
