package com.saasresearcher.learning.assessments.internal;

import com.saasresearcher.learning.assessments.api.QuestionType;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Client-supplied exam draft. IDs are honoured only when they already belong to the draft. */
record ExamDraftInput(long expectedRevision, String title, String instructions, BigDecimal passPercent, List<QuestionInput> questions) {

    record QuestionInput(UUID id, QuestionType type, String prompt, BigDecimal points, String explanation, String gradingNotes,
            List<OptionInput> options) {
    }

    record OptionInput(UUID id, String label, boolean correct) {
    }
}
