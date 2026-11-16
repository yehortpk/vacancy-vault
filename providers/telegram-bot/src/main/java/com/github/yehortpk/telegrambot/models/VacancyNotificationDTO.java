package com.github.yehortpk.telegrambot.models;

import lombok.*;

@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class VacancyNotificationDTO {
    private long chatId;
    private long filterId;
    private String filter;
    private String companyTitle;
    private String vacancyTitle;
    private Integer minSalary;
    private Integer maxSalary;
    @EqualsAndHashCode.Include
    private String link;
    private boolean isRemote;
}
