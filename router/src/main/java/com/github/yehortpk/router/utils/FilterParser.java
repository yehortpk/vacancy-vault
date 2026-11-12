package com.github.yehortpk.router.utils;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class responsible for parsing filter into matches categories.
 * <p>Mandatory matches - word has to be presented in the input</p>
 * <p>Negative matches - word must not be presented in the input</p>
 * <p>Binary matches - one of the words from (word1|word2...) has to be presented in the input</p>
 */
@Getter
public class FilterParser {
    private String companyMatch;
    private final List<String> multiChoiceMatches = new ArrayList<>();
    private final List<String> negativeMatches = new ArrayList<>();
    private final List<String> mandatoryMatches = new ArrayList<>();
    private final String FILTER_REGEX = "^(.*,)?\\s*(\\([^)]+\\))?\\s*([^\\-\\s,][^\\-]*)?\\s*((?:\\s*-[^\\-\\s]+)+)?\\s*$";

    /**
     * The input is lowercased before matching, so the words are too; a blank word, left by a
     * double space, would otherwise be "contained" in every input and veto every vacancy.
     */
    private static List<String> normalizeWords(String[] words) {
        return Arrays.stream(words)
                .map(String::strip)
                .filter(word -> !word.isEmpty())
                .map(String::toLowerCase)
                .toList();
    }

    public FilterParser(String filter) {

        Pattern pattern = Pattern.compile(FILTER_REGEX);
        Matcher matcher = pattern.matcher(filter);

        if (matcher.matches()) {
            companyMatch = matcher.group(1);
            String multiChoice = matcher.group(2);
            String mandatory = matcher.group(3);
            String negative = matcher.group(4);

            if (companyMatch != null) {
                companyMatch = companyMatch.split(",")[0];
            }

            if (mandatory != null) {
                mandatoryMatches.addAll(normalizeWords(mandatory.split("\\s+")));
            }

            if (negative != null) {
                negativeMatches.addAll(normalizeWords(negative.replace("-", "").split("\\s+")));
            }

            if (multiChoice != null) {
                String[] binaryParts = multiChoice
                        .replace("(", "")
                        .replace(")", "")
                        .split("\\|");
                multiChoiceMatches.addAll(normalizeWords(binaryParts));
            }
        } else {
            throw  new RuntimeException("Parser couldn't match this filter: " + filter);
        }
    }

    /**
     * Whether a vacancy passes the filter. The company keyword is matched against the start of
     * this string, so the company title comes first, followed by the vacancy title.
     */
    public boolean isVacancyApplicable(String companyTitle, String vacancyTitle) {
        return isStringApplicable((companyTitle == null ? "" : companyTitle) + " "
                + (vacancyTitle == null ? "" : vacancyTitle));
    }

    public boolean isStringApplicable(String string) {
        if(companyMatch != null && !string.startsWith(companyMatch)) {
            return false;
        }

        string = string.toLowerCase();

        for (String negativeMatch : negativeMatches) {
            if (string.contains(negativeMatch)) {
                return false;
            }
        }

        for (String defaultMatch : mandatoryMatches) {
            if (!string.contains(defaultMatch)) {
                return false;
            }
        }

        boolean result = true;
        for (String multiChoiceMatch : multiChoiceMatches) {
            if (string.contains(multiChoiceMatch)) {
                return true;
            }
            result = false;
        }

        return result;
    }
}
