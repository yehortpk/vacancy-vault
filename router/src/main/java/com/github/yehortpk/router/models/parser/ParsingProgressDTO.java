package com.github.yehortpk.router.models.parser;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor
@Getter
@AllArgsConstructor
@Builder
public class ParsingProgressDTO {
    private String parsingHash;
    private ParsingStatusEnum status;
    @Builder.Default
    private boolean finished = false;
    @Setter
    private List<ParserProgress> parsers = new ArrayList<>();

    @JsonProperty("total")
    @Builder.Default
    private int parsedVacanciesTotalCount = 0;
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
