package com.github.yehortpk.crawler.utils;

public class LogUtils {
    public static String createErrorMessage(Exception e) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("\n").append("Error: ").append("\n");
        stringBuilder.append(e.getLocalizedMessage()).append("\n\n");

        stringBuilder.append("Error stack trace: ").append("\n").append("\n");
        for (StackTraceElement stackTraceElement : e.getStackTrace()) {
            stringBuilder.append(stackTraceElement).append("\n\n");
        }

        if (e.getCause() != null) {
            stringBuilder.append("Error cause stack trace: ").append("\n").append("\n");
            for (StackTraceElement stackTraceElement : e.getCause().getStackTrace()) {
                stringBuilder.append(stackTraceElement).append("\n\n");
            }
        }

        return stringBuilder.toString();
    }
}
