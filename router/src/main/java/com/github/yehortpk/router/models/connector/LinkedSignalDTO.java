package com.github.yehortpk.router.models.connector;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * A provider service reporting that the user finished linking.
 *
 * <p>{@code token} is the one-time token from the link session and is the user's proof.
 * {@code destination} holds the fields that provider's own code reads (Telegram:
 * {@code chatId}); the core passes them through without looking at them. {@code title} is
 * an optional default label, such as a group chat's name.
 */
public record LinkedSignalDTO(
        @NotBlank String token,
        @Size(max = 255) String title,
        @NotNull Map<String, String> destination) {
}
