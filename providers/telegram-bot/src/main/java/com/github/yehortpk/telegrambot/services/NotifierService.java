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
        
        if (vacancy.getMinSalary() != 0 || vacancy.getMaxSalary() != 0) {
            String salaryPart = "Salary: " + (vacancy.getMaxSalary() == 0 ? "not specified" :
                    vacancy.getMinSalary() == 0 ?
                            String.format("%s", vacancy.getMaxSalary()) :
                            String.format("%s - %s", vacancy.getMinSalary(), vacancy.getMaxSalary())) + "\n";
            stringBuilder.append(salaryPart);
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
}
