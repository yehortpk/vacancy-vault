package com.github.yehortpk.crawler.models.history;

import com.github.yehortpk.crawler.progress.CrawlerProgress;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CrawlerPageLog {
    @Field("log_id")
    private int id;
    @Field("log_level")
    private CrawlerProgress.LogLevelEnum level;
    @Field("log_timestamp")
    private LocalDateTime timestamp;
    @Field("log_message")
    private String message;
}
