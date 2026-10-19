package com.saasresearcher.learning.courses.internal;

import com.saasresearcher.learning.common.ApiException;
import com.saasresearcher.learning.courses.api.RequiredExamValidator;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** Applies the registered exam validator to a course edition's required exam list. */
@Component
class RequiredExams {

    private final ObjectProvider<RequiredExamValidator> validator;

    RequiredExams(ObjectProvider<RequiredExamValidator> validator) {
        this.validator = validator;
    }

    void require(UUID courseId, List<UUID> examVersionIds) {
        if (examVersionIds.isEmpty()) {
            return;
        }
        validator.getObject().problem(courseId, examVersionIds)
                .ifPresent(problem -> {
                    throw ApiException.invalid("requiredExamVersionIds", problem);
                });
    }
}
