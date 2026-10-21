package com.saasresearcher.learning.assessments.repository;

import com.saasresearcher.learning.assessments.api.QuestionType;
import com.saasresearcher.learning.assessments.entity.ExamQuestion.Option;
import com.saasresearcher.learning.assessments.entity.ExamQuestion;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Questions and options of an exam edition; only drafts are replaced. */
@Repository
public class ExamContentRepository {

    private final JdbcClient jdbc;

    ExamContentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<ExamQuestion> questions(UUID versionId) {
        Map<UUID, List<Option>> options = new HashMap<>();
        jdbc.sql("""
                select o.question_id, o.id, o.label, o.correct from question_options o join exam_questions q on q.id = o.question_id
                where q.exam_version_id = :version order by o.question_id, o.position
                """)
                .param("version", versionId)
                .query(rs -> {
                    options.computeIfAbsent(rs.getObject("question_id", UUID.class), id -> new ArrayList<>())
                            .add(new Option(rs.getObject("id", UUID.class), rs.getString("label"), rs.getBoolean("correct")));
                });
        return jdbc.sql("""
                select id, type, prompt, points, explanation, grading_notes from exam_questions
                where exam_version_id = :version order by position
                """)
                .param("version", versionId)
                .query((rs, row) -> {
                    UUID id = rs.getObject("id", UUID.class);
                    return new ExamQuestion(id, QuestionType.valueOf(rs.getString("type")), rs.getString("prompt"), rs.getBigDecimal("points"),
                            rs.getString("explanation"), rs.getString("grading_notes"), options.getOrDefault(id, List.of()));
                })
                .list();
    }

    public Set<UUID> questionIds(UUID versionId) {
        return Set.copyOf(jdbc.sql("select id from exam_questions where exam_version_id = :version").param("version", versionId).query(UUID.class).list());
    }

    public Set<UUID> optionIds(UUID versionId) {
        return Set.copyOf(jdbc.sql("""
                select o.id from question_options o join exam_questions q on q.id = o.question_id where q.exam_version_id = :version
                """).param("version", versionId).query(UUID.class).list());
    }

    public void replace(UUID versionId, List<ExamQuestion> questions) {
        jdbc.sql("delete from question_options where question_id in (select id from exam_questions where exam_version_id = :version)")
                .param("version", versionId).update();
        jdbc.sql("delete from exam_questions where exam_version_id = :version").param("version", versionId).update();
        for (int q = 0; q < questions.size(); q++) {
            ExamQuestion question = questions.get(q);
            jdbc.sql("""
                    insert into exam_questions (id, exam_version_id, position, type, prompt, points, explanation, grading_notes)
                    values (:id, :version, :position, :type, :prompt, :points, :explanation, :notes)
                    """)
                    .param("id", question.id()).param("version", versionId).param("position", q).param("type", question.type().name())
                    .param("prompt", question.prompt()).param("points", question.points()).param("explanation", question.explanation())
                    .param("notes", question.gradingNotes())
                    .update();
            for (int o = 0; o < question.options().size(); o++) {
                Option option = question.options().get(o);
                jdbc.sql("insert into question_options (id, question_id, position, label, correct) values (:id, :question, :position, :label, :correct)")
                        .param("id", option.id()).param("question", question.id()).param("position", o).param("label", option.label())
                        .param("correct", option.correct())
                        .update();
            }
        }
    }
}
