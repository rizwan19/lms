package com.saasresearcher.learning.assessments.api;

import java.util.UUID;

public interface ExamCatalog {

    PublishedExam requirePublished(UUID examVersionId);
}
