package com.github.yehortpk.router.providers.telegram;

import lombok.*;

@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TelegramNotificationDTO {
    private long chatId;
    private long filterId;
    private String filter;
    private String companyTitle;
    private String vacancyTitle;
    private int minSalary;
    private int maxSalary;
    @EqualsAndHashCode.Include
    private String link;
    private boolean isRemote;
}
