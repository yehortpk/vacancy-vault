package com.github.yehortpk.crawler.models;

import com.github.yehortpk.crawler.exceptions.UnresolvedPlaceholderException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScrapeSettingsTest {
    private static ScrapeSettings createSettings() {
        return ScrapeSettings.builder()
                .pageId(2)
                .pageURL("https://example.com/jobs/page-{page}")
                .data(Map.of("page", "{page}", "token", "{token}", "city", "Kyiv"))
                .headers(Map.of("X-Token", "Bearer {token}"))
                .cookies(Map.of("session", "{token}"))
                .build();
    }

    @Test
    void FillPlaceholders_PlaceholdersInUrlDataAndHeaders_ShouldReplaceAll() {
        ScrapeSettings filled = createSettings().fillPlaceholders(Map.of("page", "2", "token", "abc"));

        assertEquals("https://example.com/jobs/page-2", filled.getPageURL());
        assertEquals(Map.of("page", "2", "token", "abc", "city", "Kyiv"), filled.getData());
        assertEquals(Map.of("X-Token", "Bearer abc"), filled.getHeaders());
    }

    @Test
    void FillPlaceholders_Cookies_ShouldLeaveThemUnchanged() {
        ScrapeSettings filled = createSettings().fillPlaceholders(Map.of("page", "2", "token", "abc"));

        assertEquals(Map.of("session", "{token}"), filled.getCookies());
    }

    @Test
    void FillPlaceholders_PlaceholderWithoutValue_ShouldThrowNamingIt() {
        UnresolvedPlaceholderException exception = assertThrows(UnresolvedPlaceholderException.class,
                () -> createSettings().fillPlaceholders(Map.of("page", "2")));

        assertEquals("{token}", exception.getPlaceholder());
        assertTrue(exception.getMessage().contains("{token}"));
    }

    @Test
    void FillPlaceholders_ValueWithRegexSpecialCharacters_ShouldInsertItLiterally() {
        ScrapeSettings settings = ScrapeSettings.builder().pageURL("https://example.com/{token}").build();

        ScrapeSettings filled = settings.fillPlaceholders(Map.of("token", "a$1\\b"));

        assertEquals("https://example.com/a$1\\b", filled.getPageURL());
    }

    @Test
    void FillPlaceholders_JsonLikeValue_ShouldNotTakeItForPlaceholder() {
        ScrapeSettings settings = ScrapeSettings.builder().data(Map.of("filter", "{\"city\": \"Kyiv\"}")).build();

        ScrapeSettings filled = settings.fillPlaceholders(Map.of());

        assertEquals(Map.of("filter", "{\"city\": \"Kyiv\"}"), filled.getData());
    }

    @Test
    void FillPlaceholders_UnsetFields_ShouldStayNull() {
        ScrapeSettings filled = ScrapeSettings.builder().build().fillPlaceholders(Map.of("page", "1"));

        assertNull(filled.getPageURL());
        assertNull(filled.getData());
        assertNull(filled.getHeaders());
        assertNull(filled.getCookies());
    }

    @Test
    void FillPlaceholders_AnySettings_ShouldKeepOriginalAndOtherFields() {
        ScrapeSettings settings = createSettings().toBuilder().timeoutSec(10).followRedirects(false).build();

        ScrapeSettings filled = settings.fillPlaceholders(Map.of("page", "2", "token", "abc"));

        assertEquals("https://example.com/jobs/page-{page}", settings.getPageURL());
        assertEquals(2, filled.getPageId());
        assertEquals(10, filled.getTimeoutSec());
        assertFalse(filled.isFollowRedirects());
    }

    @Test
    void HasPlaceholder_ValueWithAndWithoutPlaceholder_ShouldTellThemApart() {
        assertTrue(ScrapeSettings.hasPlaceholder("page-{page}"));
        assertFalse(ScrapeSettings.hasPlaceholder("{\"city\": \"Kyiv\"}"));
        assertFalse(ScrapeSettings.hasPlaceholder(null));
    }
}
