package com.saasresearcher.learning.assessments.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(name = "attempt_answers")
public class AttemptAnswer {

    @Id
    private UUID id;

    @Column(name = "attempt_id", nullable = false, updatable = false)
    private UUID attemptId;

    @Column(name = "question_id", nullable = false, updatable = false)
    private UUID questionId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "selected_option_ids", nullable = false)
    private List<UUID> selectedOptionIds;

    @Column(name = "written_text")
    private String writtenText;

    @Column(name = "saved_at", nullable = false)
    private Instant savedAt;

    protected AttemptAnswer() {
    }

    public AttemptAnswer(UUID attemptId, UUID questionId) {
        this.id = UUID.randomUUID();
        this.attemptId = attemptId;
        this.questionId = questionId;
        this.selectedOptionIds = List.of();
    }

    public void save(List<UUID> selectedOptionIds, String writtenText, Instant now) {
        this.selectedOptionIds = List.copyOf(selectedOptionIds);
        this.writtenText = writtenText;
        this.savedAt = now;
    }
}
