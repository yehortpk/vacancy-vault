package com.github.yehortpk.router.models.connection;

/**
 * Where a link session stands, derived from its timestamps rather than stored.
 *
 * <p>This is the "linked" signal the account panel waits on after starting a link.
 */
public enum LinkSessionStatus {
    PENDING,
    LINKED,
    EXPIRED
}
