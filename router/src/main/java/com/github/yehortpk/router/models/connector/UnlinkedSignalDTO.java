package com.github.yehortpk.router.models.connector;

import jakarta.validation.constraints.NotNull;

import java.util.Map;

/** A provider service reporting that a destination should stop receiving notifications. */
public record UnlinkedSignalDTO(@NotNull Map<String, String> destination) {
}
