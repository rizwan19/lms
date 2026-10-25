package com.saasresearcher.learning.grading.service;

import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.common.PageQuery;
import com.saasresearcher.learning.common.PageResponse;
import com.saasresearcher.learning.courses.api.CourseOwnershipService;
import com.saasresearcher.learning.grading.api.RevisionStatus;
import com.saasresearcher.learning.grading.repository.GradeRevisionRepository;
import com.saasresearcher.learning.grading.dto.GradeViews.QueueView;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Read model of a course's finalized attempts and their grade state; oldest unpublished work first. Author only. */
@Component
public class GradingQueue {

    public enum Filter { PENDING, PUBLISHED, ALL }

    private final GradeRevisionRepository revisions;
    private final CourseOwnershipService ownership;

    GradingQueue(GradeRevisionRepository revisions, CourseOwnershipService ownership) {
        this.revisions = revisions;
        this.ownership = ownership;
    }

    @Transactional(readOnly = true)
    public PageResponse<QueueView> list(UUID courseId, UUID courseVersionId, Filter filter, PageQuery page, Actor actor) {
        ownership.requireAuthor(courseId, actor);
        return PageResponse.of(revisions.queue(courseId, courseVersionId == null, courseVersionId == null ? courseId : courseVersionId, filter.name(),
                page.pageable(Sort.unsorted())), row -> new QueueView(row.getAttemptId(), row.getCourseVersionId(), row.getLearnerName(), row.getUsername(),
                        row.getExamTitle(), row.getAttemptNumber(), row.getFinalizedAt(), RevisionStatus.fromCode(row.getGradeStatus()), row.getRevisionNumber(),
                        row.getPublishedEarned(), row.getMaximum()));
    }
}
