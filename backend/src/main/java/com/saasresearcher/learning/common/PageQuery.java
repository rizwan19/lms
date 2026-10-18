package com.saasresearcher.learning.common;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Zero-based page request; size defaults to 25 and is capped at 100. */
public record PageQuery(int page, int size) {

    public static final int DEFAULT_SIZE = 25;
    public static final int MAX_SIZE = 100;

    public PageQuery {
        page = Math.max(page, 0);
        size = size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
    }

    public static PageQuery of(Integer page, Integer size) {
        return new PageQuery(page == null ? 0 : page, size == null ? DEFAULT_SIZE : size);
    }

    public Pageable pageable(Sort sort) {
        return PageRequest.of(page, size, sort);
    }

    public long offset() {
        return (long) page * size;
    }
}
