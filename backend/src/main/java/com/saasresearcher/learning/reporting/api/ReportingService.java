package com.saasresearcher.learning.reporting.api;

import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.common.PageQuery;
import com.saasresearcher.learning.common.PageResponse;
import java.util.UUID;

/** Author-only course reports; {@code versionId} filters to one edition (scores default to the latest one). */
public interface ReportingService {

    PageResponse<LearnerProgressRow> courseProgress(UUID courseId, UUID versionId, Actor actor, PageQuery query);

    PageResponse<LearnerScoreRow> courseScores(UUID courseId, UUID versionId, Actor actor, PageQuery query);

    /** Queues a CSV export into private storage; the requester is notified when it is ready. */
    UUID requestExport(UUID courseId, UUID versionId, ReportType type, Actor actor);
}
