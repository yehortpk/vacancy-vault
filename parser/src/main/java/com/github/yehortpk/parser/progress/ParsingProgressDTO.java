package com.github.yehortpk.parser.progress;

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
    @Builder.Default
    private boolean finished = false;
    @Setter
    @Builder.Default
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
    @JsonProperty("finished_at")
    @Setter
    private LocalDateTime finishedAt;
}
