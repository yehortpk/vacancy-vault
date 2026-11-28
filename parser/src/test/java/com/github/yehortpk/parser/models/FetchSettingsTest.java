package com.github.yehortpk.parser.models;

import com.github.yehortpk.parser.exceptions.UnresolvedPlaceholderException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FetchSettingsTest {
    private static FetchSettings createSettings() {
        return FetchSettings.builder()
                .pageId(2)
                .pageURL("https://example.com/jobs/page-{page}")
                .data(Map.of("page", "{page}", "token", "{token}", "city", "Kyiv"))
                .headers(Map.of("X-Token", "Bearer {token}"))
                .cookies(Map.of("session", "{token}"))
                .build();
    }

    @Test
    void FillPlaceholders_PlaceholdersInUrlDataAndHeaders_ShouldReplaceAll() {
        FetchSettings filled = createSettings().fillPlaceholders(Map.of("page", "2", "token", "abc"));

        assertEquals("https://example.com/jobs/page-2", filled.getPageURL());
        assertEquals(Map.of("page", "2", "token", "abc", "city", "Kyiv"), filled.getData());
        assertEquals(Map.of("X-Token", "Bearer abc"), filled.getHeaders());
    }

    @Test
    void FillPlaceholders_Cookies_ShouldLeaveThemUnchanged() {
        FetchSettings filled = createSettings().fillPlaceholders(Map.of("page", "2", "token", "abc"));

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
        FetchSettings settings = FetchSettings.builder().pageURL("https://example.com/{token}").build();

        FetchSettings filled = settings.fillPlaceholders(Map.of("token", "a$1\\b"));

        assertEquals("https://example.com/a$1\\b", filled.getPageURL());
    }

    @Test
    void FillPlaceholders_JsonLikeValue_ShouldNotTakeItForPlaceholder() {
        FetchSettings settings = FetchSettings.builder().data(Map.of("filter", "{\"city\": \"Kyiv\"}")).build();

        FetchSettings filled = settings.fillPlaceholders(Map.of());

        assertEquals(Map.of("filter", "{\"city\": \"Kyiv\"}"), filled.getData());
    }

    @Test
    void FillPlaceholders_UnsetFields_ShouldStayNull() {
        FetchSettings filled = FetchSettings.builder().build().fillPlaceholders(Map.of("page", "1"));

        assertNull(filled.getPageURL());
        assertNull(filled.getData());
        assertNull(filled.getHeaders());
        assertNull(filled.getCookies());
    }

    @Test
    void FillPlaceholders_AnySettings_ShouldKeepOriginalAndOtherFields() {
        FetchSettings settings = createSettings().toBuilder().timeoutSec(10).followRedirects(false).build();

        FetchSettings filled = settings.fillPlaceholders(Map.of("page", "2", "token", "abc"));

        assertEquals("https://example.com/jobs/page-{page}", settings.getPageURL());
        assertEquals(2, filled.getPageId());
        assertEquals(10, filled.getTimeoutSec());
        assertFalse(filled.isFollowRedirects());
    }

    @Test
    void HasPlaceholder_ValueWithAndWithoutPlaceholder_ShouldTellThemApart() {
        assertTrue(FetchSettings.hasPlaceholder("page-{page}"));
        assertFalse(FetchSettings.hasPlaceholder("{\"city\": \"Kyiv\"}"));
        assertFalse(FetchSettings.hasPlaceholder(null));
    }
}
