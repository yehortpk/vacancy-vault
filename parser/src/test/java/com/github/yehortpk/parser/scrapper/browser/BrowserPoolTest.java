package com.github.yehortpk.parser.scrapper.browser;

import com.github.yehortpk.parser.services.ParsingProgressService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BrowserPoolTest {
    private static final long GB = 1024L * 1024 * 1024;

    private BrowserPool createPool(String configuredPoolSize) {
        return new BrowserPool(configuredPoolSize, new ParsingProgressService());
    }

    @Test
    void CalculatePoolSize_ManyCpusAndPlentyOfMemory_ShouldUseHalfTheCpus() {
        assertEquals(3, BrowserPool.calculatePoolSize(6, 16 * GB));
    }

    @Test
    void CalculatePoolSize_MemoryTighterThanCpus_ShouldFitIntoMemoryLeftAfterReserve() {
        assertEquals(2, BrowserPool.calculatePoolSize(16, 4 * GB));
    }

    @Test
    void CalculatePoolSize_SmallHost_ShouldKeepOneInstance() {
        assertEquals(1, BrowserPool.calculatePoolSize(1, 2 * GB));
    }

    @Test
    void BrowserPool_ExplicitPoolSize_ShouldNotFail() {
        createPool("4");
    }

    @Test
    void BrowserPool_MaxPoolSize_ShouldNotFail() {
        createPool("max");
    }

    @Test
    void BrowserPool_ZeroPoolSize_ShouldFail() {
        assertThrows(IllegalArgumentException.class, () -> createPool("0"));
    }

    @Test
    void BrowserPool_NonNumericPoolSize_ShouldFail() {
        assertThrows(IllegalArgumentException.class, () -> createPool("many"));
    }
}
