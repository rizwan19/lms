package com.saasresearcher.learning.courses.service;

import com.saasresearcher.learning.common.ApiException;
import com.saasresearcher.learning.common.PageQuery;
import com.saasresearcher.learning.common.PageResponse;
import com.saasresearcher.learning.courses.api.CourseSection;
import com.saasresearcher.learning.courses.api.CourseStatus;
import com.saasresearcher.learning.courses.dto.CatalogViews.CatalogCourse;
import com.saasresearcher.learning.courses.dto.CatalogViews.CatalogCourseDetail;
import com.saasresearcher.learning.courses.dto.CatalogViews.EditionSummary;
import com.saasresearcher.learning.courses.dto.CatalogViews.OutlineSection;
import com.saasresearcher.learning.courses.entity.Course;
import com.saasresearcher.learning.courses.entity.CourseVersion;
import com.saasresearcher.learning.courses.api.EditionStatus;
import com.saasresearcher.learning.courses.repository.CourseRepository;
import com.saasresearcher.learning.courses.repository.CourseVersionRepository;
import com.saasresearcher.learning.identity.api.AccountSummary;
import com.saasresearcher.learning.identity.api.IdentityAccess;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Public discovery of active published courses; every field comes from the latest published edition. */
@Service
public class PublicCatalogService {

    private static final int SUMMARY_LENGTH = 500;

    private final CourseRepository courses;
    private final CourseVersionRepository versions;
    private final CourseContent content;
    private final IdentityAccess identity;

    PublicCatalogService(CourseRepository courses, CourseVersionRepository versions, CourseContent content, IdentityAccess identity) {
        this.courses = courses;
        this.versions = versions;
        this.content = content;
        this.identity = identity;
    }

    @Transactional(readOnly = true)
    public PageResponse<CatalogCourse> search(String search, UUID authorId, PageQuery query) {
        String pattern = "%" + (search == null ? "" : search.trim().toLowerCase(Locale.ROOT).replace("%", "").replace("_", "")) + "%";
        Page<Course> page = courses.catalog(pattern, authorId, query.pageable(Sort.by("title", "id")));
        Map<UUID, AccountSummary> authors = identity.summaries(page.map(Course::getAuthorId).toList());
        return PageResponse.of(page, course -> {
            CourseVersion latest = latest(course.getId());
            String description = latest.getDescription();
            return new CatalogCourse(course.getId(), latest.getTitle(), description.length() > SUMMARY_LENGTH ? description.substring(0, SUMMARY_LENGTH) : description,
                    authors.get(course.getAuthorId()).displayName(), edition(latest, content.readTree(latest.getId())));
        });
    }

    @Transactional(readOnly = true)
    public CatalogCourseDetail detail(UUID courseId) {
        Course course = courses.findById(courseId).filter(found -> found.getStatus() == CourseStatus.ACTIVE).orElseThrow(ApiException::notFound);
        CourseVersion latest = versions.findFirstByCourseIdAndStatusOrderByEditionDesc(courseId, EditionStatus.PUBLISHED).orElseThrow(ApiException::notFound);
        List<CourseSection> tree = content.readTree(latest.getId());
        String author = identity.summaries(List.of(course.getAuthorId())).get(course.getAuthorId()).displayName();
        List<OutlineSection> outline = tree.stream()
                .map(section -> new OutlineSection(section.title(), section.lessons().stream().map(lesson -> lesson.title()).toList()))
                .toList();
        return new CatalogCourseDetail(course.getId(), latest.getTitle(), latest.getDescription(), author, edition(latest, tree), outline);
    }

    private CourseVersion latest(UUID courseId) {
        return versions.findFirstByCourseIdAndStatusOrderByEditionDesc(courseId, EditionStatus.PUBLISHED).orElseThrow();
    }

    private static EditionSummary edition(CourseVersion version, List<CourseSection> tree) {
        return new EditionSummary(version.getEdition(), version.getPublishedAt(), tree.size(), tree.stream().mapToInt(section -> section.lessons().size()).sum());
    }
}
