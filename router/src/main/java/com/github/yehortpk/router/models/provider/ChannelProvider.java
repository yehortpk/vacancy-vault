package com.github.yehortpk.router.models.provider;

/**
 * The messenger platforms the notifier can deliver through.
 *
 * <p>Mirrors {@code provider.code}. The enum is the compile-time half of the catalog: code
 * selects a {@code NotificationProvider} implementation by it, while the table row carries
 * the display title and the enabled flag.
 *
 * <p>"Provider" rather than "channel" everywhere in the domain, because channel already
 * means a broadcast channel in Telegram's own vocabulary.
 */
public enum ChannelProvider {
    TELEGRAM
}
