package com.saasresearcher.learning.courses.controller;

import com.saasresearcher.learning.common.PageQuery;
import com.saasresearcher.learning.common.PageResponse;
import com.saasresearcher.learning.courses.dto.CatalogViews.CatalogCourse;
import com.saasresearcher.learning.courses.dto.CatalogViews.CatalogCourseDetail;
import com.saasresearcher.learning.courses.service.PublicCatalogService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public, unauthenticated course discovery. Summaries only; access is requested through the enrollment routes. */
@RestController
@RequestMapping("/catalog/courses")
public class CatalogController {

    private final PublicCatalogService catalog;

    CatalogController(PublicCatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    PageResponse<CatalogCourse> search(@RequestParam(required = false) String search, @RequestParam(required = false) UUID authorId,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return catalog.search(search, authorId, PageQuery.of(page, size));
    }

    @GetMapping("/{id}")
    CatalogCourseDetail detail(@PathVariable UUID id) {
        return catalog.detail(id);
    }
}
