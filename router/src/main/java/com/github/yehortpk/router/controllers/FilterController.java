package com.github.yehortpk.router.controllers;

import com.github.yehortpk.router.models.filter.FilterDTO;
import com.github.yehortpk.router.models.filter.FilterRequestDTO;
import com.github.yehortpk.router.models.vacancy.VacanciesPageDTO;
import com.github.yehortpk.router.security.AuthenticatedUser;
import com.github.yehortpk.router.services.FilterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller responsible for the caller's own filters.
 *
 * <p>The owner is taken from the authenticated principal, never from the request. A filter
 * or connection id that belongs to someone else answers 404, like one that does not exist.
 */
@RestController
@RequestMapping("/me/filters")
@RequiredArgsConstructor
public class FilterController {
    private final FilterService filterService;

    @GetMapping
    public List<FilterDTO> getFilters(@AuthenticationPrincipal AuthenticatedUser principal) {
        return filterService.findFilters(principal.getUserId());
    }

    /** {@code connectionIds} empty or omitted creates a draft, which notifies nobody. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FilterDTO createFilter(@AuthenticationPrincipal AuthenticatedUser principal,
                                  @Valid @RequestBody FilterRequestDTO request) {
        return filterService.createFilter(principal.getUserId(), request);
    }

    @GetMapping("/{filter_id}")
    public FilterDTO getFilter(@AuthenticationPrincipal AuthenticatedUser principal,
                               @PathVariable("filter_id") long filterId) {
        return filterService.findFilter(filterId, principal.getUserId());
    }

    /** Replaces the title, the expression and the bindings. */
    @PutMapping("/{filter_id}")
    public FilterDTO updateFilter(@AuthenticationPrincipal AuthenticatedUser principal,
                                  @PathVariable("filter_id") long filterId,
                                  @Valid @RequestBody FilterRequestDTO request) {
        return filterService.updateFilter(filterId, principal.getUserId(), request);
    }

    @DeleteMapping("/{filter_id}")
    public void deleteFilter(@AuthenticationPrincipal AuthenticatedUser principal,
                             @PathVariable("filter_id") long filterId) {
        filterService.deleteFilter(filterId, principal.getUserId());
    }

    @GetMapping("/{filter_id}/vacancies")
    public VacanciesPageDTO getVacanciesByFilter(@AuthenticationPrincipal AuthenticatedUser principal,
                                                 @PathVariable("filter_id") long filterId,
                                                 @RequestParam(value = "page") int pageId,
                                                 @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
                                                 @RequestParam(value = "sortBy", defaultValue = "parsedAt") String sortBy,
                                                 @RequestParam(value = "sortDir", defaultValue = "DESC") String sortDir) {
        return filterService.findVacanciesPage(filterId, principal.getUserId(), pageId, pageSize, sortBy, sortDir);
    }
}
