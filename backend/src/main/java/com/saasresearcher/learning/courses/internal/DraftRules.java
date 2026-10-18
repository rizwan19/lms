package com.saasresearcher.learning.courses.internal;

import com.saasresearcher.learning.common.ApiException;
import com.saasresearcher.learning.courses.api.CourseLesson;
import com.saasresearcher.learning.courses.api.CourseSection;
import com.saasresearcher.learning.courses.api.LessonVideo;
import com.saasresearcher.learning.courses.api.VideoProvider;
import com.saasresearcher.learning.courses.internal.DraftInput.LessonInput;
import com.saasresearcher.learning.courses.internal.DraftInput.SectionInput;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Validates and normalizes a draft tree: bounded sizes, sanitized HTML, provider-ID-only embeds. Collects
 * every problem keyed by its JSON path so the editor can show them in place.
 */
final class DraftRules {

    static final int MAX_TITLE = 200;
    static final int MAX_DESCRIPTION = 5000;
    static final int MAX_BODY = 200_000;
    static final int MAX_SECTIONS = 100;
    static final int MAX_LESSONS = 500;
    static final int MAX_RESOURCES = 20;

    private static final Map<VideoProvider, Pattern> PROVIDER_IDS = Map.of(
            VideoProvider.YOUTUBE, Pattern.compile("^[A-Za-z0-9_-]{11}$"),
            VideoProvider.VIMEO, Pattern.compile("^[0-9]{6,12}$"));

    private final Map<String, String> errors = new LinkedHashMap<>();

    private DraftRules() {
    }

    /** Builds the normalized tree, keeping client IDs only if they are already part of this draft. */
    static List<CourseSection> normalize(DraftInput input, Set<UUID> draftSectionIds, Set<UUID> draftLessonIds) {
        DraftRules rules = new DraftRules();
        rules.text("title", input.title(), MAX_TITLE, true);
        rules.text("description", input.description(), MAX_DESCRIPTION, false);
        List<SectionInput> sections = input.sections() == null ? List.of() : input.sections();
        if (sections.size() > MAX_SECTIONS) {
            rules.errors.put("sections", "At most " + MAX_SECTIONS + " sections");
        }
        List<CourseSection> result = new ArrayList<>();
        int lessonCount = 0;
        for (int s = 0; s < sections.size(); s++) {
            SectionInput section = sections.get(s);
            String path = "sections[" + s + "]";
            rules.text(path + ".title", section.title(), MAX_TITLE, true);
            List<LessonInput> lessons = section.lessons() == null ? List.of() : section.lessons();
            List<CourseLesson> normalized = new ArrayList<>();
            for (int l = 0; l < lessons.size(); l++) {
                normalized.add(rules.lesson(path + ".lessons[" + l + "]", lessons.get(l), draftLessonIds));
            }
            lessonCount += lessons.size();
            result.add(new CourseSection(keep(section.id(), draftSectionIds), trim(section.title()), normalized));
        }
        if (lessonCount > MAX_LESSONS) {
            rules.errors.put("sections", "At most " + MAX_LESSONS + " lessons");
        }
        if (!rules.errors.isEmpty()) {
            throw ApiException.invalid(rules.errors);
        }
        return result;
    }

    private CourseLesson lesson(String path, LessonInput lesson, Set<UUID> draftLessonIds) {
        text(path + ".title", lesson.title(), MAX_TITLE, true);
        String body = lesson.bodyHtml() == null ? "" : lesson.bodyHtml();
        if (body.length() > MAX_BODY) {
            errors.put(path + ".bodyHtml", "Lesson text is too long");
        }
        List<UUID> resources = lesson.resourceAssetIds() == null ? List.of() : lesson.resourceAssetIds();
        if (resources.size() > MAX_RESOURCES || resources.stream().distinct().count() != resources.size()) {
            errors.put(path + ".resourceAssetIds", "Use up to " + MAX_RESOURCES + " different files");
        }
        return new CourseLesson(keep(lesson.id(), draftLessonIds), trim(lesson.title()), LessonHtml.sanitize(body),
                video(path, lesson), resources, lesson.required());
    }

    private LessonVideo video(String path, LessonInput lesson) {
        boolean hasProvider = lesson.videoProvider() != null || (lesson.videoRef() != null && !lesson.videoRef().isBlank());
        if (lesson.videoAssetId() != null && hasProvider) {
            errors.put(path + ".video", "Choose an uploaded video or an embed, not both");
            return null;
        }
        if (lesson.videoAssetId() != null) {
            return new LessonVideo(lesson.videoAssetId(), null, null);
        }
        if (!hasProvider) {
            return null;
        }
        String ref = lesson.videoRef() == null ? "" : lesson.videoRef().trim();
        if (lesson.videoProvider() == null || !PROVIDER_IDS.get(lesson.videoProvider()).matcher(ref).matches()) {
            errors.put(path + ".videoRef", "Enter the video ID from YouTube or Vimeo, not a link");
            return null;
        }
        return new LessonVideo(null, lesson.videoProvider(), ref);
    }

    private void text(String path, String value, int max, boolean required) {
        String trimmed = trim(value);
        if (required && trimmed.isEmpty()) {
            errors.put(path, "Required");
        } else if (trimmed.length() > max) {
            errors.put(path, "At most " + max + " characters");
        }
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static UUID keep(UUID id, Set<UUID> existing) {
        return id != null && existing.contains(id) ? id : UUID.randomUUID();
    }
}
