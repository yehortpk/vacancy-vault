package com.github.yehortpk.router.models.parser;

import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;
import java.util.List;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "parsing-history")
public class ParsingProgress {
    @Id
    private String id;

    @Indexed(unique = true)
    @Field("parsing_hash")
    private String  parsingHash;
    @Field("parsed_vacancies")
    private Integer parsedVacancies;
    @Field("new_vacancies")
    private Integer newVacancies;
    @Field("outdated_vacancies")
    private Integer outdatedVacancies;
    @Field("status")
    private ParsingStatusEnum status;
    @Field("finished")
    private Boolean finished;
    @Field("parsers")
    private List<ParserProgress> parsers;
    @Field("started_at")
    private LocalDateTime startedAt;
    @Field("finished_at")
    private LocalDateTime finishedAt;
}
