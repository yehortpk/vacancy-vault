package com.github.yehortpk.router.models.user;

/**
 * What an account may reach beyond its own data. Mirrors {@code app_user.role}.
 *
 * <p>{@code USER} is every registered account: its own control room under {@code /me}.
 * {@code ADMIN} additionally drives the parser. Anonymous visitors have no role at all and
 * only see the public dashboard.
 */
public enum UserRole {
    USER,
    ADMIN
}
