package com.github.yehortpk.router.models.filter;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Creates or replaces a filter. {@code connectionIds} empty or omitted makes it a draft; on an
 * update the list replaces the filter's bindings as a whole.
 */
public record FilterRequestDTO(
        @NotBlank @Size(max = 255) String title,
        @NotBlank String filter,
        List<@NotNull Long> connectionIds) {
}
