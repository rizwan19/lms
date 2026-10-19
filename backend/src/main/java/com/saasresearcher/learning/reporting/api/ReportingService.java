package com.saasresearcher.learning.reporting.api;

import com.saasresearcher.learning.common.Actor;
import com.saasresearcher.learning.common.PageQuery;
import com.saasresearcher.learning.common.PageResponse;
import java.util.UUID;

public interface ReportingService {

    PageResponse<StudentProgressRow> classProgress(UUID classId, Actor actor, PageQuery query);

    PageResponse<StudentScoreRow> classScores(UUID classId, Actor actor, PageQuery query);

    /** Queues a CSV export into private storage; the requester is notified when it is ready. */
    UUID requestExport(UUID classId, ReportType type, Actor actor);
}
