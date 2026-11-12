package com.github.yehortpk.router.models.filter;

import java.util.List;

/**
 * A filter as its owner sees it. An empty {@code connectionIds} marks a draft.
 */
public record FilterDTO(
        long filterId,
        String title,
        String filter,
        List<Long> connectionIds) {
}
