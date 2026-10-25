package com.saasresearcher.learning.courses.service;

import com.saasresearcher.learning.courses.api.CourseLesson;
import com.saasresearcher.learning.courses.api.CourseSection;
import com.saasresearcher.learning.courses.entity.Lesson;
import com.saasresearcher.learning.courses.entity.Section;
import com.saasresearcher.learning.courses.repository.LessonRepository;
import com.saasresearcher.learning.courses.repository.SectionRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Section/lesson/resource trees of an edition. Only drafts are ever replaced. */
@Component
public class CourseContent {

    private final SectionRepository sections;
    private final LessonRepository lessons;

    CourseContent(SectionRepository sections, LessonRepository lessons) {
        this.sections = sections;
        this.lessons = lessons;
    }

    List<CourseSection> readTree(UUID versionId) {
        Map<UUID, List<CourseLesson>> bySection = new LinkedHashMap<>();
        lessons.findByCourseVersionIdOrderByPosition(versionId)
                .forEach(lesson -> bySection.computeIfAbsent(lesson.getSectionId(), id -> new ArrayList<>()).add(lesson.toLesson()));
        return sections.findByCourseVersionIdOrderByPosition(versionId).stream()
                .map(section -> new CourseSection(section.getId(), section.getTitle(), bySection.getOrDefault(section.getId(), List.of())))
                .toList();
    }

    /** Replaces the whole tree: deletes and flushes first, so kept IDs and positions can be inserted again. */
    void replaceTree(UUID versionId, List<CourseSection> tree) {
        lessons.deleteByCourseVersionId(versionId);
        lessons.flush();
        sections.deleteByCourseVersionId(versionId);
        sections.flush();
        for (int s = 0; s < tree.size(); s++) {
            CourseSection section = tree.get(s);
            sections.save(new Section(section.id(), versionId, section.title(), s));
        }
        sections.flush();
        for (CourseSection section : tree) {
            for (int l = 0; l < section.lessons().size(); l++) {
                lessons.save(new Lesson(versionId, section.id(), l, section.lessons().get(l)));
            }
        }
        lessons.flush();
    }

    Set<UUID> sectionIds(UUID versionId) {
        return sections.findByCourseVersionIdOrderByPosition(versionId).stream().map(Section::getId).collect(Collectors.toSet());
    }

    Set<UUID> lessonIds(UUID versionId) {
        return lessons.findByCourseVersionIdOrderByPosition(versionId).stream().map(Lesson::getId).collect(Collectors.toSet());
    }
}
