package com.github.yehortpk.router.utils;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * Turns the router's zoneless timestamps into ones a browser can place.
 *
 * <p>Timestamps are stored as {@code TIMESTAMP} without a zone and written with
 * {@link LocalDateTime#now()}, i.e. in the router's own zone. Sent as they are, a browser reads
 * them as its local time and is off by the difference; with the offset attached, it converts
 * them correctly.
 */
public final class ServerClock {
    private ServerClock() {
    }

    public static OffsetDateTime toOffsetDateTime(LocalDateTime timestamp) {
        return timestamp == null ? null : timestamp.atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }
}
