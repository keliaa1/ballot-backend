package com.ballot.dto.common;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Paginated response wrapper mirroring Spring's Page structure for API consumers.
 */
@Getter
@Builder
public class PageResponse<T> {

    private final List<T> content;
    private final int page;
    private final int size;
    private final long totalElements;
    private final int totalPages;
    private final boolean first;
    private final boolean last;
}
