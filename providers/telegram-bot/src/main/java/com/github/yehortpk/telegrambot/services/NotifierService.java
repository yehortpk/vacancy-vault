package com.github.yehortpk.telegrambot.services;

import com.github.yehortpk.telegrambot.models.VacancyNotificationDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotifierService {
    private final TelegramMessageSender telegramMessageSender;

    public void notifyUser(VacancyNotificationDTO notification) {
        long chatId = notification.getChatId();
        telegramMessageSender.sendMessageWithoutMarkup(chatId, getFormattedMessage(notification));
    }

    private String getFormattedMessage(VacancyNotificationDTO vacancy){
        StringBuilder stringBuilder = new StringBuilder();
        String titlePart = String.format("<b>%s</b> in <i>%s</i>\n", vacancy.getVacancyTitle(), vacancy.getCompanyTitle());
        stringBuilder.append(titlePart);
        
        String salaryRange = formatSalaryRange(vacancy.getMinSalary(), vacancy.getMaxSalary());
        if (salaryRange != null) {
            stringBuilder.append("Salary: ").append(salaryRange);
            if (vacancy.getSalaryCurrency() != null) {
                stringBuilder.append(" ").append(vacancy.getSalaryCurrency());
            }
            stringBuilder.append("\n");
        }

        if (vacancy.isRemote()) {
            stringBuilder.append(" remote");
        }

        String linkPart = String.format("<a href='%s'>%s</a>\n", vacancy.getLink(), vacancy.getLink());
        stringBuilder.append(linkPart);

        String filterPart = String.format("\nFilter: <code>%s</code>\n", vacancy.getFilter());
        stringBuilder.append(filterPart);


        return stringBuilder.toString();
    }

    /** A range such as "1000 - 3000", or null when the source published neither bound. */
    private static String formatSalaryRange(Integer minSalary, Integer maxSalary) {
        if (minSalary != null && maxSalary != null) {
            return minSalary.equals(maxSalary) ? String.valueOf(minSalary) : minSalary + " - " + maxSalary;
        }
        if (minSalary != null) {
            return "from " + minSalary;
        }
        if (maxSalary != null) {
            return "up to " + maxSalary;
        }
        return null;
    }
}
